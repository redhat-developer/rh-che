/*
 * Copyright (c) 2018-2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.che.start_workspace_reporter.model;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ZabbixHistoryParams {

  private static final Gson gson = new Gson();
  private static final JsonParser parser = new JsonParser();

  private static final Integer DEFAULT_HISTORY_ENTRY_TYPE = 0;
  private static final Integer DEFAULT_RESULTS_LIMIT = 1000;
  private static final String DEFAULT_SORT_ELEMENT = SortField.CLOCK.toString();
  private static final String DEFAULT_SORT_ORDER = SortOrder.ASC.toString();
  private static final Set<String> DEFAULT_PRINT_TYPE = Collections.singleton("extend");

  private Integer history;
  private Set<String> hostids;
  private Set<String> itemids;
  private Long time_from;
  private Long time_till;
  private String sortfield;
  private String sortorder;
  private Integer limit;
  private Set<String> output;
  private Set<String> selectitems;

  public ZabbixHistoryParams() {
    this.history = DEFAULT_HISTORY_ENTRY_TYPE;
    this.sortfield = DEFAULT_SORT_ELEMENT;
    this.sortorder = DEFAULT_SORT_ORDER;
    this.limit = DEFAULT_RESULTS_LIMIT;
    this.output = DEFAULT_PRINT_TYPE;
  }

  public ZabbixHistoryParams(
      Integer history,
      Set<String> hostids,
      Set<String> itemids,
      Long time_from,
      Long time_till,
      String sortfield,
      String sortorder,
      Integer limit,
      Set<String> output,
      Set<String> selectitems) {
    this.history = (history == null) ? DEFAULT_HISTORY_ENTRY_TYPE : history;
    this.hostids = hostids;
    this.itemids = itemids;
    this.time_from = time_from;
    this.time_till = time_till;
    this.sortfield = (sortfield == null) ? DEFAULT_SORT_ELEMENT : sortfield;
    this.sortorder = (sortorder == null) ? DEFAULT_SORT_ORDER : sortorder;
    this.limit = (limit == null) ? DEFAULT_RESULTS_LIMIT : limit;
    this.output = (output == null) ? DEFAULT_PRINT_TYPE : output;
    this.selectitems = selectitems;
  }

  public Integer getHistory() {
    return history;
  }

  public void setHistory(Integer history) {
    this.history = history;
  }

  public Set<String> getHostids() {
    return hostids;
  }

  public void setHostids(String host) {
    this.hostids = new HashSet<>();
    this.hostids.add(host);
  }

  public void setHostids(Set<String> hostids) {
    this.hostids = hostids;
  }

  public Set<String> getItemids() {
    return itemids;
  }

  public void setItemids(String itemid) {
    this.itemids = new HashSet<>();
    this.itemids.add(itemid);
  }

  public void setItemids(Set<String> itemids) {
    this.itemids = itemids;
  }

  public Long getTime_from() {
    return time_from;
  }

  public void setTime_from(Long time_from) {
    this.time_from = time_from;
  }

  public Long getTime_till() {
    return time_till;
  }

  public void setTime_till(Long time_till) {
    this.time_till = time_till;
  }

  public String getSortfield() {
    return sortfield;
  }

  public void setSortfield(String sortfield) {
    this.sortfield = sortfield;
  }

  public String getSortorder() {
    return sortorder;
  }

  public void setSortorder(String sortorder) {
    this.sortorder = sortorder;
  }

  public Integer getLimit() {
    return limit;
  }

  public void setLimit(Integer limit) {
    this.limit = limit;
  }

  public Set<String> getOutput() {
    return output;
  }

  public void setOutput(Set<String> output) {
    this.output = output;
  }

  public void setOutput(String output) {
    this.output = new HashSet<>();
    this.output.add(output);
  }

  public Set<String> getSelectitems() {
    return selectitems;
  }

  public void setSelectitems(Set<String> selectitems) {
    this.selectitems = selectitems;
  }

  public void setSelectItems(String selectitem) {
    this.selectitems = new HashSet<>();
    this.selectitems.add(selectitem);
  }

  public enum SortField {
    ITEMID("itemid"),
    CLOCK("clock");

    private String value;

    SortField(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }
  }

  public enum SortOrder {
    ASC("ASC"),
    DESC("DESC");

    private String value;

    SortOrder(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }
  }
}
