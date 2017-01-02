/*
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
package org.apache.nutch.crawl;

import org.apache.hadoop.io.Writable;
import org.apache.nutch.jobs.io.WebPageWritable;
import org.apache.nutch.util.GenericWritableConfigurable;

@SuppressWarnings("unchecked")
public class NutchWritable extends GenericWritableConfigurable {

  private static Class<? extends Writable>[] CLASSES = null;

  static {
    CLASSES = (Class<? extends Writable>[]) new Class<?>[] {
//        WebEdge.class,
        WebPageWritable.class };
  }

  public NutchWritable() {
  }

  public NutchWritable(Writable instance) {
    set(instance);
  }

//  public NutchWritable reset(WebEdge edge) {
//    set(edge);
//    return this;
//  }
//
//  public NutchWritable reset(WebPageWritable webPageWritable) {
//    set(webPageWritable);
//    return this;
//  }

  @Override
  protected Class<? extends Writable>[] getTypes() {
    return CLASSES;
  }

}
