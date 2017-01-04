/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.apache.nutch.jobs.update;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.util.StringUtils;
import org.apache.nutch.graph.*;
import org.apache.nutch.graph.io.WebGraphWritable;
import org.apache.nutch.jobs.NutchCounter;
import org.apache.nutch.jobs.NutchMapper;
import org.apache.nutch.persist.WebPage;
import org.apache.nutch.persist.gora.GoraWebPage;
import org.apache.nutch.scoring.ScoringFilterException;
import org.apache.nutch.scoring.ScoringFilters;
import org.apache.nutch.util.Params;
import org.apache.nutch.util.StringUtil;
import org.apache.nutch.util.TableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.apache.nutch.jobs.NutchCounter.Counter.rows;
import static org.apache.nutch.metadata.Nutch.PARAM_CRAWL_ID;
import static org.apache.nutch.metadata.Nutch.PARAM_GENERATOR_MAX_DISTANCE;
import static org.apache.nutch.persist.Mark.FETCH;

class OutGraphUpdateMapper extends NutchMapper<String, GoraWebPage, GraphGroupKey, WebGraphWritable> {

  public static final Logger LOG = LoggerFactory.getLogger(OutGraphUpdateMapper.class);

  public enum Counter { rowsMapped, newRowsMapped, notFetched, urlFiltered, tooDeep }

  private Configuration conf;
  private Mapper.Context context;
  private NutchCounter counter;

  private int maxDistance = Integer.MAX_VALUE;
  private int maxOutlinks = 1000;

  private ScoringFilters scoringFilters;

  @Override
  public void setup(Context context) throws IOException, InterruptedException {
    super.setup(context);

    conf = context.getConfiguration();
    this.context = context;

    counter = getCounter();
    counter.register(Counter.class);

    String crawlId = conf.get(PARAM_CRAWL_ID);

    maxDistance = conf.getInt(PARAM_GENERATOR_MAX_DISTANCE, Integer.MAX_VALUE);
    scoringFilters = new ScoringFilters(conf);

    LOG.info(Params.format(
        "className", this.getClass().getSimpleName(),
        "crawlId", crawlId,
        "maxDistance", maxDistance,
        "maxOutlinks", maxOutlinks
    ));
  }

  @Override
  public void map(String reversedUrl, GoraWebPage row, Context context) throws IOException, InterruptedException {
    counter.increase(rows);

    WebPage page = WebPage.wrap(row);

    if (!page.hasMark(FETCH)) {
      counter.increase(Counter.notFetched);
      return;
    }

    final int depth = page.getDepth();
    if (depth >= maxDistance) {
      counter.increase(Counter.tooDeep);
      return;
    }

    String url = TableUtil.unreverseUrl(reversedUrl);
    WebGraph graph = new WebGraph();
    WebVertex v1 = new WebVertex(url, page);

    /* A loop in the graph */
    graph.addVertex(v1);
    graph.addEdge(v1, v1);

    page.getOutlinks().entrySet().forEach(l -> graph.addEdgeLenient(v1, new WebVertex(l.getKey())).setAnchor(l.getValue()));

    try {
      scoringFilters.distributeScoreToOutlinks(url, page, graph, graph.outgoingEdgesOf(v1), graph.outDegreeOf(v1));
    } catch (ScoringFilterException e) {
      LOG.warn("Distributing score failed for URL: " + reversedUrl + "\n" + StringUtils.stringifyException(e));
    }

    graph.outgoingEdgesOf(v1).forEach(edge -> writeAsSubGraph(edge, graph));

    counter.increase(Counter.rowsMapped);
    counter.increase(Counter.newRowsMapped, graph.outDegreeOf(v1));
  }

  /**
   * The following graph shows the in-link graph. Every reduce group contains a center vertex and a batch of edges.
   * The center vertex has a web page inside, and the edges has in-link information.
   *
   *        v1
   *        |
   *        v
   * v2 -> vc <- v3
   *       ^ ^
   *      /  \
   *     v4  v5
   * */
  private void writeAsSubGraph(WebEdge edge, WebGraph graph) {
    try {
      WebGraph subgraph = WebGraph.of(edge, graph);
      WebGraphWritable graphWritable = new WebGraphWritable(subgraph, conf);

      String reverseUrl = TableUtil.reverseUrl(edge.getTargetUrl());
      // noinspection unchecked
      context.write(new GraphGroupKey(reverseUrl, graph.getEdgeWeight(edge)), graphWritable);
    } catch (IOException|InterruptedException e) {
      LOG.error("Failed to write to hdfs. " + StringUtil.stringifyException(e));
    }
  }
}