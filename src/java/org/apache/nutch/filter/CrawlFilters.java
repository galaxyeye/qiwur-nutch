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
package org.apache.nutch.filter;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.nutch.filter.CrawlFilter.PageCategory;
import org.apache.nutch.util.DateTimeUtil;
import org.apache.nutch.util.NutchConfiguration;
import org.apache.nutch.util.ObjectCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * TODO : need full unit test
 * TODO : Move to plugin, urlfilter/contentfilter, etc
 * */
public class CrawlFilters extends Configured {

  public static final Logger LOG = LoggerFactory.getLogger(CrawlFilters.class);

  public static final String CRAWL_FILTER_RULES = "crawl.filter.rules";

  @Expose
  private List<CrawlFilter> crawlFilters = Lists.newArrayList();

  public static CrawlFilters create(Configuration conf) {
    String filterRules = conf.get(CRAWL_FILTER_RULES);
//    Validate.isTrue(!filterRules.contains("\\uFFFF"));

    if (LOG.isDebugEnabled()) {
      // LOG.debug("Create CrawlFilters from Json : " + json);
    }

    ObjectCache objectCache = ObjectCache.get(conf);
    String cacheId = CrawlFilters.class.getName() + filterRules;

    if (objectCache.getObject(cacheId) != null) {
      return (CrawlFilters) objectCache.getObject(cacheId);
    } else {
      CrawlFilters filters = new CrawlFilters(conf);

      if (filterRules != null) {
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        filters = gson.fromJson(filterRules, CrawlFilters.class);
        filters.setConf(conf);
      }

      objectCache.setObject(cacheId, filters);
      return filters;
    }
  }

  public CrawlFilters(Configuration conf) {
    setConf(conf);
  }

  @Override
  public void setConf(Configuration conf) {
    if (conf == null) {
      return;
    }

    super.setConf(conf);
    for (CrawlFilter crawlFilter : crawlFilters) {
      crawlFilter.setConf(conf);
    }
  }

  public boolean testUrlSatisfied(String url) {
    if (url == null) return false;

    for (CrawlFilter filter : crawlFilters) {
      if (!filter.testUrlSatisfied(url)) {
        return false;
      }
    }

    return true;
  }

  public boolean testTextSatisfied(String text) {
    if (text == null) return false;

    for (CrawlFilter filter : crawlFilters) {
      if (!filter.testTextSatisfied(text)) {
        return false;
      }
    }

    return true;
  }

  public boolean testKeyRangeSatisfied(String reversedUrl) {
    if (reversedUrl == null) return false;

    for (CrawlFilter filter : crawlFilters) {
      if (filter.testKeyRangeSatisfied(reversedUrl)) {
        return true;
      }
    }

    return true;
  }

  public Map<String, String> getReversedKeyRanges() {
    Map<String, String> keyRanges = Maps.newHashMap();

    for (CrawlFilter filter : crawlFilters) {
      String reversedStartKey = filter.getReversedStartKey();
      String reversedEndKey = filter.getReversedEndKey();

      if (reversedStartKey != null) {
        keyRanges.put(reversedStartKey, reversedEndKey);
      }
    }

    return keyRanges;
  }

  public String[] getMaxReversedKeyRange() {
    String[] keyRange = {null, null};

    for (CrawlFilter filter : crawlFilters) {
      String reversedStartKey = filter.getReversedStartKey();
      String reversedEndKey = filter.getReversedEndKey();

      if (reversedStartKey != null) {
        if (keyRange[0] == null) {
          keyRange[0] = reversedStartKey;
        }
        else if (CrawlFilter.keyLessEqual(reversedStartKey, keyRange[0])) {
          keyRange[0] = reversedStartKey;
        }
      }

      if (reversedEndKey != null) {
        if (keyRange[1] == null) {
          keyRange[1] = reversedEndKey;
        }
        else if (CrawlFilter.keyGreaterEqual(reversedEndKey, keyRange[1])) {
          keyRange[1] = reversedEndKey;
        }
      }
    }

    return keyRange;
  }

  /**
   * TODO : Tricky logic
   * */
  public boolean isAllowed(Node node) {
    if (node == null) {
      return false;
    }

    if (crawlFilters.isEmpty()) {
      return true;
    }

    for (CrawlFilter filter : crawlFilters) {
      if (filter.isAllowed(node)) {
        return true;
      }
    }

    return false;
  }

  /**
   * TODO : Tricky logic
   * */
  public boolean isDisallowed(Node node) {
    if (node == null) {
      return true;
    }

    if (isAllowed(node)) {
      return false;
    }

    for (CrawlFilter filter : crawlFilters) {
      if (filter.isDisallowed(node)) {
        return true;
      }
    }

    return false;
  }

  /**
   * For urls who contains date information, for example
   * http://bond.hexun.com/2011-01-07/126641872.html
   * */
  public boolean containsOldDateString(String url) {
    return DateTimeUtil.containsOldDate(url);
  }

  public boolean isDetailUrl(String url) {
    if (url == null) {
      return false;
    }

    PageCategory pageType = CrawlFilter.sniffPageCategoryByUrlPattern(url);

    if (pageType == PageCategory.DETAIL) {
      return true;
    }

    for (CrawlFilter filter : crawlFilters) {
      if (filter.isDetailUrl(url)) {
        return true;
      }
    }

    return false;
  }

  public boolean isIndexUrl(String url) {
    if (url == null) return false;

    PageCategory pageType = CrawlFilter.sniffPageCategoryByUrlPattern(url);

    if (pageType.isIndex()) {
      return true;
    }

    for (CrawlFilter filter : crawlFilters) {
      if (filter.isIndexUrl(url)) {
        return true;
      }
    }

    return false;
  }

  public boolean isMediaUrl(String url) {
    if (url == null) return false;

    PageCategory pageType = CrawlFilter.sniffPageCategoryByUrlPattern(url);

    if (pageType == PageCategory.MEDIA) {
      return true;
    }

    for (CrawlFilter filter : crawlFilters) {
      if (filter.isMediaUrl(url)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Notice : index url is not a search url even if it contains "search"
   * */
  public boolean isSearchUrl(String url) {
    if (url == null) {
      return false;
    }

    PageCategory pageType = CrawlFilter.sniffPageCategoryByUrlPattern(url);

    if (pageType == PageCategory.SEARCH) {
      return true;
    }

    for (CrawlFilter filter : crawlFilters) {
      if (filter.isSearchUrl(url)) {
        return true;
      }
    }

    return false;
  }

  public List<CrawlFilter> getCrawlFilters() {
    return crawlFilters;
  }

  public void setCrawlFilters(List<CrawlFilter> crawlFilters) {
    this.crawlFilters = crawlFilters;
  }

  public String toJson() {
    Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    return gson.toJson(this);
  }

  @Override
  public String toString() {
    return toJson();
  }

  public static void main(String[] args) throws Exception {
    Configuration conf = NutchConfiguration.create();
    byte[] json = Files.readAllBytes(Paths.get("/tmp/crawl_filters.json"));
    conf.set(CRAWL_FILTER_RULES, new String(json));

    String url = "http://item.yhd.com/item/12342134134.html";

    CrawlFilters crawlFilters = CrawlFilters.create(conf);
    System.out.println(crawlFilters);

    boolean detail = crawlFilters.isDetailUrl("http://item.yhd.com/item/12342134134.html");
    System.out.println("detail " + detail);

    System.out.println("----------------");
    RegexURLFilter urlFilter = new RegexURLFilter("+^http://item.yhd.com/item/(.+)$");
    urlFilter.filter(url);
  }
}
