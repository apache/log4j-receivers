--  Licensed to the Apache Software Foundation (ASF) under one or more
--  contributor license agreements.  See the NOTICE file distributed with
--  this work for additional information regarding copyright ownership.
--  The ASF licenses this file to You under the Apache License, Version 2.0
--  (the "License"); you may not use this file except in compliance with
--  the License.  You may obtain a copy of the License at
-- 
--       http://www.apache.org/licenses/LICENSE-2.0
--
--  Unless required by applicable law or agreed to in writing, software
--  distributed under the License is distributed on an "AS IS" BASIS,
--  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
--  See the License for the specific language governing permissions and
--  limitations under the License.
--
--
-- This SQL script creates the required tables by org.apache.log4j.db.DBAppender and 
-- org.apache.log4j.db.DBReceiver. 
-- 
-- It is intended for MS SQL Server databases.  This has been tested with version 7.0. 

DROP TABLE logging_event_property 
DROP TABLE logging_event_exception 
DROP TABLE logging_event 

CREATE TABLE logging_event 
  ( 
    sequence_number   DECIMAL(20) NOT NULL, 
    timestamp         DECIMAL(20) NOT NULL, 
    rendered_message  VARCHAR(4000) NOT NULL, 
    logger_name       VARCHAR(254) NOT NULL, 
    level_string      VARCHAR(254) NOT NULL, 
    ndc               VARCHAR(4000), 
    thread_name       VARCHAR(254), 
    reference_flag    SMALLINT, 
    caller_filename   VARCHAR(254) NOT NULL,
    caller_class      VARCHAR(254) NOT NULL,
    caller_method     VARCHAR(254) NOT NULL,
    caller_line       CHAR(4) NOT NULL,
    event_id          INT NOT NULL identity, 
    PRIMARY KEY(event_id) 
  ) 

CREATE TABLE logging_event_property 
  ( 
    event_id          INT NOT NULL, 
    mapped_key        VARCHAR(254) NOT NULL, 
    mapped_value      VARCHAR(1024), 
    PRIMARY KEY(event_id, mapped_key), 
    FOREIGN KEY (event_id) REFERENCES logging_event(event_id) 
  ) 

CREATE TABLE logging_event_exception 
  ( 
    event_id         INT NOT NULL, 
    i                SMALLINT NOT NULL, 
    trace_line       VARCHAR(254) NOT NULL, 
    PRIMARY KEY(event_id, i), 
    FOREIGN KEY (event_id) REFERENCES logging_event(event_id) 
  ) 

