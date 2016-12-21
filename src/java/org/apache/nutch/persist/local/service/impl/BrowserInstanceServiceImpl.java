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
package org.apache.nutch.persist.local.service.impl;

import java.sql.SQLException;
import java.util.List;

import javax.annotation.Resource;

import org.apache.nutch.persist.local.model.BrowserInstance;
import org.apache.nutch.persist.local.service.BrowserInstanceService;
import org.springframework.stereotype.Service;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.table.TableUtils;

@Service
public class BrowserInstanceServiceImpl implements BrowserInstanceService {

  @Resource
  private Dao<BrowserInstance, Long> browserInstanceDao;

  @Override
  public boolean authorize(BrowserInstance browserInstance) {
    try {
      QueryBuilder<BrowserInstance, Long> queryBuilder = browserInstanceDao.queryBuilder();
      queryBuilder.where()
        .eq("id", browserInstance.getId())
        .eq("password", browserInstance.getPassword());
      PreparedQuery<BrowserInstance> preparedQuery = queryBuilder.prepare();

      long count = browserInstanceDao.countOf(preparedQuery);

      return count != 0;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void save(BrowserInstance browserInstance) {
    try {
      browserInstanceDao.createOrUpdate(browserInstance);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void delete(Long id) {
    try {
      browserInstanceDao.deleteById(id);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<BrowserInstance> list() {
    try {
      return browserInstanceDao.queryForAll();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public BrowserInstance get(Long id) {
    try {
      return browserInstanceDao.queryForId(id);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void truncate() {
    try {
      TableUtils.dropTable(browserInstanceDao.getConnectionSource(), BrowserInstance.class, true);
      TableUtils.createTableIfNotExists(browserInstanceDao.getConnectionSource(), BrowserInstance.class);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
