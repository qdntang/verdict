/*
 * Copyright 2018 University of Michigan
 * 
 * You must contact Barzan Mozafari (mozafari@umich.edu) or Yongjoo Park (pyongjoo@umich.edu) to discuss
 * how you could use, modify, or distribute this code. By default, this code is not open-sourced and we do
 * not license this code.
 */

package org.verdictdb.core.querying;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.tuple.Pair;
import org.verdictdb.core.execution.ExecutableNode;
import org.verdictdb.core.execution.ExecutablePlan;
import org.verdictdb.core.querying.ola.AsyncAggExecutionNode;
import org.verdictdb.core.scrambling.ScrambleMetaSet;
import org.verdictdb.core.sqlobject.BaseTable;
import org.verdictdb.core.sqlobject.SelectQuery;
import org.verdictdb.core.sqlobject.SubqueryColumn;
import org.verdictdb.exception.VerdictDBException;
import org.verdictdb.exception.VerdictDBTypeException;
import org.verdictdb.exception.VerdictDBValueException;

public class QueryExecutionPlan implements ExecutablePlan, IdCreator, Serializable {
  
  private static final long serialVersionUID = 8377795890801468660L;

  protected ScrambleMetaSet scrambleMeta;

  protected ExecutableNodeBase root;

  protected IdCreator idCreator;

  public QueryExecutionPlan(String scratchpadSchemaName) {
    this.scrambleMeta = new ScrambleMetaSet();
    this.idCreator = new TempIdCreatorInScratchpadSchema(scratchpadSchemaName);
  }

  public QueryExecutionPlan(String scratchpadSchemaName, ScrambleMetaSet scrambleMeta) {
    this.idCreator = new TempIdCreatorInScratchpadSchema(scratchpadSchemaName);
    this.scrambleMeta = scrambleMeta;
  }

  /**
   * 
   * @param query  A well-formed select query object
   * @throws VerdictDBValueException 
   * @throws VerdictDBException 
   */
  public QueryExecutionPlan(
      String scratchpadSchemaName,
      ScrambleMetaSet scrambleMeta,
      SelectQuery query) throws VerdictDBException {
    
    this(scratchpadSchemaName);
    setScrambleMeta(scrambleMeta);
    setSelectQuery(query);
  }
  
  public QueryExecutionPlan(String scratchpadSchemaName, ExecutableNodeBase root) {
    this(scratchpadSchemaName);
    this.root = root;
  }

  public int getSerialNumber() {
    return ((TempIdCreatorInScratchpadSchema) idCreator).getSerialNumber();
  }

  public ScrambleMetaSet getScrambleMeta() {
    return scrambleMeta;
  }

  public void setScrambleMeta(ScrambleMetaSet scrambleMeta) {
    this.scrambleMeta = scrambleMeta;
  }

  public void setSelectQuery(SelectQuery query) throws VerdictDBException {
    // TODO: this should also test if subqueries include aggregates
    // Change this to something like 'doesContainSupportedAggregateInDescendents()'.
    if (!query.isSupportedAggregate()) {
      throw new VerdictDBTypeException(query);
    }
    this.root = makePlan(query);
  }

  public String getScratchpadSchemaName() {
    return ((TempIdCreatorInScratchpadSchema) idCreator).getScratchpadSchemaName();
  }

  public ExecutableNodeBase getRootNode() {
    return root;
  }

  public void setRootNode(ExecutableNodeBase root) {
    this.root = root;
  }

  /** 
   * Creates a tree in which each node is QueryExecutionNode. Each AggQueryExecutionNode corresponds to
   * an aggregate query, whether it is the main query or a subquery.
   * 
   * 1. Each QueryExecutionNode is supposed to run on a separate thread.
   * 2. Restrict the aggregate subqueries to appear in the where clause or in the from clause 
   *    (i.e., not in the select list, not in having or group-by)
   * 3. Each node cannot include any correlated predicate (i.e., the column that appears in outer queries).
   *   (1) In the future, we should convert a correlated subquery into a joined subquery (if possible).
   *   (2) Otherwise, the entire query including a correlated subquery must be the query of a single node.
   * 4. The results of AggNode and ProjectionNode are stored as a materialized view; the names of those
   *    materialized views are passed to their parents for potential additional processing or reporting.
   * 
   * //@param conn
   * @param query
   * @return Pair of roots of the tree and post-processing interface.
   * @throws VerdictDBValueException 
   * @throws VerdictDBTypeException 
   */
  ExecutableNodeBase makePlan(SelectQuery query) throws VerdictDBException {
    ExecutableNodeBase root = SelectAllExecutionNode.create(idCreator, query);
    return root;
  }

  // clean up any intermediate materialized tables
  public void cleanUp() {
    ((TempIdCreatorInScratchpadSchema) idCreator).reset();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.DEFAULT_STYLE)
        .append("root", root)
        .append("scrambleMeta", scrambleMeta)
        .toString();
  }

  public ExecutableNodeBase getRoot() {
    return root;
  }
  
  @Override
  public List<Integer> getNodeGroupIDs() {
    return Arrays.asList(0);
  }

  @Override
  public List<ExecutableNode> getNodesInGroup(int groupId) {
    List<ExecutableNode> nodes = new ArrayList<>();
    List<ExecutableNodeBase> pool = new LinkedList<>();
    pool.add(root);
    while (!pool.isEmpty()) {
      ExecutableNodeBase n = pool.remove(0);
      if (nodes.contains(n)) {
        continue;
      }
      nodes.add(n);
      pool.addAll(n.getExecutableNodeBaseDependents());
    }
    return nodes;
  }

  @Override
  public ExecutableNode getReportingNode() {
    return root;
  }

  @Override
  public String generateAliasName() {
    return idCreator.generateAliasName();
  }

  @Override
  public Pair<String, String> generateTempTableName() {
    return idCreator.generateTempTableName();
  }

  public QueryExecutionPlan deepcopy() {
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(bos);
      out.writeObject(this);
      out.flush();
      out.close();

      ObjectInputStream in = new ObjectInputStream(
          new ByteArrayInputStream(bos.toByteArray()));
      return (QueryExecutionPlan) in.readObject();
    } catch (ClassNotFoundException | IOException e) {
      e.printStackTrace();
    }
    return null;
  }
}
