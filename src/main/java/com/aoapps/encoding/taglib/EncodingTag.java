/*
 * ao-encoding-taglib - High performance streaming character encoding in a JSP environment.
 * Copyright (C) 2020, 2021, 2022, 2023  AO Industries, Inc.
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

import com.aoapps.encoding.MediaType;
import com.aoapps.lang.Coercion;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * @author  AO Industries, Inc.
 */
public class EncodingTag extends EncodingFilteredTag {

  @Override
  public MediaType getContentType() {
    return mediaType;
  }

  /* BodyTag only:
    private static final long serialVersionUID = 1L;
  /**/

  private MediaType mediaType;

  public void setType(Object type) throws IOException {
    type = Coercion.trimNullIfEmpty(type);
    String typeStr = (type == null) ? null : Coercion.toString(type);
    MediaType newMediaType = MediaType.getMediaTypeByName(typeStr);
    if (newMediaType == null) {
      try {
        newMediaType = MediaType.getMediaTypeForContentType(typeStr);
      } catch (UnsupportedEncodingException e) {
        throw new IllegalArgumentException(e);
      }
    }
    this.mediaType = newMediaType;
  }
}
