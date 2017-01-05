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
package org.apache.nutch.jobs.samples;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.nutch.jobs.NutchMapper;
import org.apache.nutch.persist.WebPage;
import org.apache.nutch.persist.gora.GoraWebPage;
import org.apache.nutch.util.TableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.apache.nutch.jobs.NutchCounter.Counter.rows;
import static org.apache.nutch.metadata.Nutch.PARAM_LIMIT;

public class SampleMapper extends NutchMapper<String, GoraWebPage, Text, GoraWebPage> {

  public static final Logger LOG = LoggerFactory.getLogger(SampleJob.class);

  public enum Counter { rowsMapped, newRowsMapped, notFetched, urlFiltered }

  private Configuration conf;
  private int limit = -1;
  private int count = 0;

  @Override
  public void setup(Context context) throws IOException, InterruptedException {
    super.setup(context);

    conf = context.getConfiguration();

    getCounter().register(Counter.class);

    limit = conf.getInt(PARAM_LIMIT, -1);
  }

  /**
   * One row map to several rows
   * */
  @Override
  public void map(String reversedUrl, GoraWebPage row, Context context) throws IOException, InterruptedException {
    if (limit > 0 && count++ > limit) {
      stop("Hit limit, stop");
      return;
    }

    getCounter().increase(rows);

    WebPage page = WebPage.wrap(row);

    String url = TableUtil.unreverseUrl(reversedUrl);
    LOG.debug("Map : " + url);

//    if (!page.hasMark(Mark.FETCH)) {
//      getCounter().increase(Counter.notFetched);
//      return;
//    }

    for (int i = 0; i < 5; ++i) {
      page.getInlinks().put("http://www.example.com/mapper" + i, String.valueOf(i));
    }

    context.write(new Text(reversedUrl), page.get());
  }
}
