/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
// Licensed to Julian Hyde under one or more contributor license
// agreements. See the NOTICE file distributed with this work for
// additional information regarding copyright ownership.
//
// Julian Hyde licenses this file to you under the Modified BSD License
// (the "License"); you may not use this file except in compliance with
// the License. You may obtain a copy of the License at:
//
// http://opensource.org/licenses/BSD-3-Clause
*/
package sqlline;

import java.sql.SQLException;

// CHECKSTYLE: IGNORE 0
import sun.misc.Signal;
import sun.misc.SignalHandler;

/**
 * A signal handler for SQLLine which interprets Ctrl+C as a request to cancel
 * the currently executing query. Adapted from
 * <a href="http://www.smotricz.com/kabutz/Issue043.html">TJSN</a>.
 */
class SunSignalHandler implements SqlLineSignalHandler, SignalHandler {
  private DispatchCallback dispatchCallback;

  SunSignalHandler() {
    Signal.handle(new Signal("INT"), this);
  }

  public void setCallback(DispatchCallback dispatchCallback) {
    this.dispatchCallback = dispatchCallback;
  }

  public void handle(Signal sig) {
    try {
      synchronized (this) {
        if (dispatchCallback != null) {
          dispatchCallback.forceKillSqlQuery();
          dispatchCallback.setToCancel();
        }
      }
    } catch (SQLException ex) {
      throw new RuntimeException(ex);
    }
  }
}

// End SunSignalHandler.java
