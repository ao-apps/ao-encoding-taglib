/*
 * ao-encoding-taglib - High performance streaming character encoding in a JSP environment.
 * Copyright (C) 2020, 2021, 2022  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-encoding-taglib.
 *
 * ao-encoding-taglib is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-encoding-taglib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-encoding-taglib.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoapps.encoding.taglib;

import com.aoapps.lang.ThrowableSurrogateFactoryInitializer;
import com.aoapps.lang.Throwables;

/**
 * Registers Java EE Web exceptions in {@link Throwables#registerSurrogateFactory(java.lang.Class, com.aoapps.lang.ThrowableSurrogateFactory)}.
 *
 * @author  AO Industries, Inc.
 *
 * @see  com.aoapps.servlet.JavaeeWebSurrogateFactoryInitializer
 */
public class JavaeeWebSurrogateFactoryInitializer implements ThrowableSurrogateFactoryInitializer {

  @Override
  @SuppressWarnings("deprecation")
  public void run() {
    // From https://docs.oracle.com/javaee/6/api/overview-tree.html
    // JavaEE 7: Review

    // javax:javaee-web-api:6.0
    // Would add a dependency, not doing

    // javax.el:javax.el-api:2.2.5
    Throwables.registerSurrogateFactory(javax.el.ELException.class, (template, cause) ->
        new javax.el.ELException(template.getMessage(), cause)
    );
    Throwables.registerSurrogateFactory(javax.el.MethodNotFoundException.class, (template, cause) ->
        new javax.el.MethodNotFoundException(template.getMessage(), cause)
    );
    Throwables.registerSurrogateFactory(javax.el.PropertyNotFoundException.class, (template, cause) ->
        new javax.el.PropertyNotFoundException(template.getMessage(), cause)
    );
    Throwables.registerSurrogateFactory(javax.el.PropertyNotWritableException.class, (template, cause) ->
        new javax.el.PropertyNotWritableException(template.getMessage(), cause)
    );

    // javax.servlet:javax.servlet-api:3.0.1
    // Added by ao-servlet-util project

    // javax.servlet.jsp:javax.servlet.jsp-api:2.2.1
    // Added by ao-servlet-util project

    // javax.servlet:jstl:1.2
    // Would add a dependency, not doing

    // org.glassfish.web:jstl-impl:1.2
    // Would add a dependency, not doing
  }
}
