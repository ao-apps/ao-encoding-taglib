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

package com.aoapps.encoding.taglib.book;

import com.semanticcms.tagreference.TagReferenceInitializer;

public class AoEncodingLegacyTldInitializer extends TagReferenceInitializer {

  public AoEncodingLegacyTldInitializer() {
    super(
      Maven.properties.getProperty("documented.name") + " Reference (Legacy)",
      "Taglib Reference (Legacy)",
      "/encoding/taglib",
      "/ao-encoding-legacy.tld",
      true,
      Maven.properties.getProperty("documented.javadoc.link.javase"),
      Maven.properties.getProperty("documented.javadoc.link.javaee"),
      // Self
      "com.aoapps.encoding.taglib", Maven.properties.getProperty("project.url") + "apidocs/com.aoapps.encoding.taglib/",
      "com.aoapps.encoding.taglib.legacy", Maven.properties.getProperty("project.url") + "apidocs/com.aoapps.encoding.taglib/",
      // Dependencies
      "com.aoapps.encoding", "https://oss.aoapps.com/encoding/apidocs/com.aoapps.encoding/"
    );
  }
}
