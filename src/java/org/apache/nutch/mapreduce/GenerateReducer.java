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
package org.apache.nutch.mapreduce;

import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import org.apache.avro.util.Utf8;
import org.apache.nutch.mapreduce.GenerateJob.SelectorEntry;
import org.apache.nutch.storage.Mark;
import org.apache.nutch.storage.WebPage;
import org.apache.nutch.tools.NutchMetrics;
import org.apache.nutch.util.Params;
import org.apache.nutch.util.StringUtil;
import org.apache.nutch.util.TableUtil;
import org.apache.nutch.util.URLUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.MalformedURLException;

import static org.apache.nutch.mapreduce.NutchCounter.Counter.rows;
import static org.apache.nutch.metadata.Nutch.*;

/**
 * Reduce class for generate
 * 
 * The #reduce() method write a random integer to all generated URLs. This
 * random number is then used by {@link FetchMapper}.
 */
public class GenerateReducer extends NutchReducer<SelectorEntry, WebPage, String, WebPage> {

  public static final Logger LOG = GenerateJob.LOG;

  private enum Counter { hosts, hostCountTooLarge, malformedUrl }

  private long limit;
  private long maxCountPerHost;
  private URLUtil.HostGroupMode hostGroupMode;
  private Multiset<String> hostNames = LinkedHashMultiset.create();
  private String batchId;
  private NutchMetrics nutchMetrics;

  private long count = 0;

  @Override
  protected void setup(Context context) throws IOException, InterruptedException {
    super.setup(context);
    getCounter().register(Counter.class);

    String crawlId = conf.get(PARAM_CRAWL_ID);
    batchId = conf.get(PARAM_BATCH_ID, ALL_BATCH_ID_STR);

    // Generate top N links only
    limit = conf.getLong(PARAM_GENERATOR_TOP_N, Long.MAX_VALUE);
    limit /= context.getNumReduceTasks();

    maxCountPerHost = conf.getLong(PARAM_GENERATOR_MAX_TASKS_PER_HOST, 10000);
    hostGroupMode = conf.getEnum(PARAM_FETCH_QUEUE_MODE, URLUtil.HostGroupMode.BY_HOST);
    nutchMetrics = new NutchMetrics(conf);

    LOG.info(Params.format(
        "className", this.getClass().getSimpleName(),
        "crawlId", crawlId,
        "batchId", batchId,
        "limit", limit,
        "maxCountPerHost", maxCountPerHost,
        "hostGroupMode", hostGroupMode
    ));
  }

  @Override
  protected void reduce(SelectorEntry key, Iterable<WebPage> values, Context context)
      throws IOException, InterruptedException {
    getCounter().increase(rows);

    if (LOG.isTraceEnabled()) {
      LOG.trace("Generate reduce " + key.url);
    }

    String url = key.url;

    String host = URLUtil.getHost(url, hostGroupMode);
    if (host == null) {
      getCounter().increase(Counter.malformedUrl);
      return;
    }

    for (WebPage page : values) {
      if (count >= limit) {
        stop("Enough pages generated, quit");
        break;
      }

      addGeneratedHosts(host);
      if (maxCountPerHost > 0 && hostNames.count(host) > maxCountPerHost) {
        getCounter().increase(Counter.hostCountTooLarge);
        break;
      }

      Mark.INJECT_MARK.removeMarkIfExist(page);
      Mark.GENERATE_MARK.putMark(page, new Utf8(batchId));
      page.setBatchId(batchId);

      try {
        context.write(TableUtil.reverseUrl(key.url), page);

        getCounter().updateAffectedRows(key.url);
      } catch (MalformedURLException e) {
        getCounter().increase(Counter.malformedUrl);
        continue;
      }

      ++count;
    } // for
  }

  @Override
  protected void cleanup(Context context) {
    try {
      LOG.info("Generated total " + hostNames.elementSet().size() + " hosts/domains");
      nutchMetrics.reportGeneratedHosts(hostNames.elementSet(), context.getJobName());
    }
    catch (Throwable e) {
      LOG.error(StringUtil.stringifyException(e));
    }
    finally {
      super.cleanup(context);
    }
  }

  private void addGeneratedHosts(String host) throws MalformedURLException {
    if (host == null) {
      return;
    }

    hostNames.add(host);

    getCounter().setValue(Counter.hosts, hostNames.entrySet().size());
  }
}
