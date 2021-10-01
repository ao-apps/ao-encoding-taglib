/*
 * ao-encoding-taglib - High performance streaming character encoding in a JSP environment.
 * Copyright (C) 2013, 2015, 2016, 2017, 2019, 2020, 2021  AO Industries, Inc.
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
 * along with ao-encoding-taglib.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoapps.encoding.taglib;

import com.aoapps.collections.MinimalList;
import com.aoapps.encoding.MediaType;
import com.aoapps.lang.Strings;
import java.io.UnsupportedEncodingException;
import java.util.List;
import javax.servlet.jsp.tagext.TagData;
import javax.servlet.jsp.tagext.TagExtraInfo;
import javax.servlet.jsp.tagext.ValidationMessage;

/**
 * Utilities common to several {@link TagExtraInfo} implementations.
 *
 * @author  AO Industries, Inc.
 */
final public class TeiUtils {

	private TeiUtils() {}

	/**
	 * Checks that a type is a valid MediaType.
	 *
	 * @param  messages  the list of messages to add to, maybe <code>null</code>
	 *
	 * @return  the list of messages.  A new list will have been created if the <code>message</code> parameter was <code>null</code>
	 *
	 * @see  MediaType#getMediaTypeByName(java.lang.String)
	 * @see  MediaType#getMediaTypeForContentType(java.lang.String)
	 */
	// TODO: Stop using MinimalList - over-optimized
	public static List<ValidationMessage> validateMediaType(TagData data, List<ValidationMessage> messages) {
		Object typeAttr = data.getAttribute("type");
		if(
			typeAttr != null
			&& typeAttr != TagData.REQUEST_TIME_VALUE
		) {
			String type = Strings.trimNullIfEmpty((String)typeAttr); // TODO: normalizeType
			if(type != null) {
				try {
					// First allow shortcuts (matching enum names)
					MediaType mediaType = MediaType.getMediaTypeByName(type);
					if(mediaType == null) {
						// Return value not used: valdation only:
						mediaType = MediaType.getMediaTypeForContentType(type);
						assert mediaType != null;
					}
					// Value is OK
				} catch(UnsupportedEncodingException err) {
					messages = MinimalList.add(
						messages,
						new ValidationMessage(
							data.getId(),
							err.getLocalizedMessage()
						)
					);
				}
			}
		}
		return messages;
	}
}
