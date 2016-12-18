/**
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
 */

package org.apache.nutch.crawl.schedulers;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.crawl.FetchSchedule;
import org.apache.nutch.metadata.Nutch;
import org.apache.nutch.storage.WebPage;
import org.apache.nutch.util.ConfigUtils;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.apache.nutch.metadata.Nutch.TCP_IP_STANDARDIZED_TIME;

/**
 * This class implements an adaptive re-fetch algorithm. This works as follows:
 * <ul>
 * <li>for pages that has changed since the last fetchTime, decrease their
 * fetchInterval by a factor of DEC_FACTOR (default value is 0.2f).</li>
 * <li>for pages that haven't changed since the last fetchTime, increase their
 * fetchInterval by a factor of INC_FACTOR (default value is 0.2f).<br>
 * If SYNC_DELTA property is true, then:
 * <ul>
 * <li>calculate a <code>delta = fetchTime - modifiedTime</code></li>
 * <li>try to synchronize with the time of change, by shifting the next
 * fetchTime by a fraction of the difference between the last modification time
 * and the last fetch time. I.e. the next fetch time will be set to
 * <code>fetchTime + fetchInterval - delta * SYNC_DELTA_RATE</code></li>
 * <li>if the adjusted fetch interval is bigger than the delta, then
 * <code>fetchInterval = delta</code>.</li>
 * </ul>
 * </li>
 * <li>the minimum value of fetchInterval may not be smaller than MIN_INTERVAL
 * (default is 1 minute).</li>
 * <li>the maximum value of fetchInterval may not be bigger than MAX_INTERVAL
 * (default is 365 days).</li>
 * </ul>
 * <p>
 * NOTE: values of DEC_FACTOR and INC_FACTOR higher than 0.4f may destabilize
 * the algorithm, so that the fetch interval either increases or decreases
 * infinitely, with little relevance to the page changes. Please use
 * {@link #(String[])} method to test the values before applying them in a
 * production system.
 * </p>
 * 
 * @author Andrzej Bialecki
 */
public class AdaptiveFetchSchedule extends AbstractFetchSchedule {
  public static final Logger LOG = AbstractFetchSchedule.LOG;

  protected float INC_RATE = 0.2f;

  protected float DEC_RATE = 0.2f;

  protected Duration MIN_INTERVAL = Duration.ofMinutes(1);

  protected Duration MAX_INTERVAL = Duration.ofDays(365);

  protected Duration SEED_MAX_INTERVAL = Duration.ofDays(1);

  protected boolean SYNC_DELTA = true;

  protected double SYNC_DELTA_RATE = 0.2f;

  public void setConf(Configuration conf) {
    super.setConf(conf);
    if (conf == null) {
      return;
    }

    INC_RATE = conf.getFloat("db.fetch.schedule.adaptive.inc_rate", 0.2f);
    DEC_RATE = conf.getFloat("db.fetch.schedule.adaptive.dec_rate", 0.2f);
    MIN_INTERVAL = ConfigUtils.getDuration(conf, "db.fetch.schedule.adaptive.min_interval", Duration.ofMinutes(1));
    MAX_INTERVAL = ConfigUtils.getDuration(conf, "db.fetch.schedule.adaptive.max_interval", Duration.ofDays(365));
    SEED_MAX_INTERVAL = ConfigUtils.getDuration(conf, "db.fetch.schedule.adaptive.seed_max_interval", Duration.ofDays(1));
    SYNC_DELTA = conf.getBoolean("db.fetch.schedule.adaptive.sync_delta", true);
    SYNC_DELTA_RATE = conf.getFloat("db.fetch.schedule.adaptive.sync_delta_rate", 0.2f);
  }

  @Override
  public void setFetchSchedule(String url, WebPage page, Instant prevFetchTime,
                               Instant prevModifiedTime, Instant fetchTime, Instant modifiedTime, int state) {
    super.setFetchSchedule(url, page, prevFetchTime, prevModifiedTime, fetchTime, modifiedTime, state);
    if (modifiedTime.compareTo(TCP_IP_STANDARDIZED_TIME) < 0) {
      modifiedTime = fetchTime;
    }

    long interval = page.getFetchInterval(TimeUnit.SECONDS);
    switch (state) {
      case FetchSchedule.STATUS_MODIFIED:
        interval *= (1.0f - DEC_RATE);
        break;
      case FetchSchedule.STATUS_NOTMODIFIED:
        interval *= (1.0f + INC_RATE);
        break;
      case FetchSchedule.STATUS_UNKNOWN:
        break;
    }

    if (SYNC_DELTA) {
      long gap = fetchTime.getEpochSecond() - modifiedTime.getEpochSecond();
      if (gap > interval) {
        interval = gap;
      }
      fetchTime = fetchTime.minusSeconds(Math.round(gap * SYNC_DELTA_RATE));
    }

    Duration newInterval = Duration.ofSeconds(interval);
    if (newInterval.compareTo(MIN_INTERVAL) < 0) newInterval = MIN_INTERVAL;
    if (newInterval.compareTo(MAX_INTERVAL) > 0) newInterval = MAX_INTERVAL;

    updateRefetchTime(page, newInterval, fetchTime, prevModifiedTime, modifiedTime);
  }
}
