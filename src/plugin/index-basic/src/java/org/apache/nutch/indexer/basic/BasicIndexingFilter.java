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

package org.apache.nutch.indexer.basic;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.indexer.IndexDocument;
import org.apache.nutch.indexer.IndexingException;
import org.apache.nutch.indexer.IndexingFilter;
import org.apache.nutch.metadata.Nutch;
import org.apache.nutch.storage.WebPage;

import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Pattern;

// import org.apache.solr.common.util.DateUtil;

/**
 * Adds basic searchable fields to a document. The fields are: host - add host
 * as un-stored, indexed and tokenized url - url is both stored and indexed, so
 * it's both searchable and returned. This is also a required field. content -
 * content is indexed, so that it's searchable, but not stored in index title -
 * title is stored and indexed cache - add cached content/summary display
 * policy, if available tstamp - add timestamp when fetched, for deduplication
 */
public class BasicIndexingFilter implements IndexingFilter {

  private static int MAX_CONTENT_LENGTH;
  private static Pattern DETAIL_PAGE_URL_PATTERN = Pattern.compile(".+(detail|item|article|book|good|product|thread|view|/20[012][0-9]/{0,1}[01][0-9]/|\\d{10,}).+");

  private Configuration conf;

  private static final Collection<WebPage.Field> FIELDS = new HashSet<>();

  static {
    FIELDS.add(WebPage.Field.TITLE);
    FIELDS.add(WebPage.Field.TEXT);
    FIELDS.add(WebPage.Field.CONTENT);
  }

  /**
   * The {@link BasicIndexingFilter} filter object which supports boolean
   * configurable value for length of characters permitted within the title @see
   * {@code indexer.max.title.length} in nutch-default.xml
   * 
   * @param doc
   *          The {@link IndexDocument} object
   * @param url
   *          URL to be filtered for anchor text
   * @param page
   *          {@link WebPage} object relative to the URL
   * @return filtered NutchDocument
   * */
  public IndexDocument filter(IndexDocument doc, String url, WebPage page) throws IndexingException {
    addDocFields(doc, url, page);

    addPageCategory(doc, url, page);

    doc.add("outlinks_count", page.getOutlinks().size());

    doc.addIfAbsent("id", doc.getKey());

    return doc;
  }

  private void addDocFields(IndexDocument doc, String url, WebPage page) {
    page.getDocFields().entrySet().stream()
        .filter(entry -> entry.getValue().toString().length() < MAX_CONTENT_LENGTH)
        .forEach(entry -> doc.add(entry.getKey(), entry.getValue()));
  }

  private void addPageCategory(IndexDocument doc, String url, WebPage page) {
    doc.add(Nutch.DOC_FIELD_PAGE_CATEGORY, sniffPageCategory(doc, url, page));
  }

  /**
   * TODO : do it inside boilerpipe
   * */
  private String sniffPageCategory(IndexDocument doc, String url, WebPage page) {
    String pageCategory = "";

    String textContent = (String)page.getDocField(Nutch.DOC_FIELD_TEXT_CONTENT);
    if (textContent == null) {
      return pageCategory;
    }

    double _char = textContent.length();
    double _a = page.getOutlinks().size();
//    if (_char / _a < 15) {
//      pageCategory = "index";
//    }

    if (textContent.isEmpty()) {
      if (_a > 30) {
        pageCategory = "index";
      }
    }
    else if (_char > 1000) {
      pageCategory = "detail";
    }
    else if (DETAIL_PAGE_URL_PATTERN.matcher(url).matches()) {
      pageCategory = "detail";
    }
    else if (StringUtils.countMatches(url, "/") <= 3) {
      // http://t.tt/12345678
      pageCategory = "index";
    }
    else if (url.contains("index")) {
      pageCategory = "index";
    }

    return pageCategory;
  }

  /**
   * Set the {@link Configuration} object
   * */
  public void setConf(Configuration conf) {
    this.conf = conf;

    MAX_CONTENT_LENGTH = conf.getInt("indexer.max.content.length", 10 * 10000);

//    LOG.info(StringUtil.formatParamsLine(
//        "className", this.getClass().getSimpleName(),
//        "MAX_CONTENT_LENGTH", MAX_CONTENT_LENGTH
//    ));
  }

  /**
   * Get the {@link Configuration} object
   */
  public Configuration getConf() { return this.conf; }

  /**
   * Gets all the fields for a given {@link WebPage} Many datastores need to
   * setup the mapreduce job by specifying the fields needed. All extensions
   * that work on WebPage are able to specify what fields they need.
   */
  @Override
  public Collection<WebPage.Field> getFields() {
    return FIELDS;
  }

}
