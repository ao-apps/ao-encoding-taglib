/*
 * ao-encoding-taglib - High performance streaming character encoding in a JSP environment.
 * Copyright (C) 2012, 2016, 2017, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoapps.encoding.BufferedValidator;
import com.aoapps.encoding.MediaType;
import com.aoapps.encoding.MediaValidator;
import com.aoapps.encoding.ValidMediaInput;
import com.aoapps.lang.NullArgumentException;
import com.aoapps.lang.io.NullWriter;
import com.aoapps.servlet.attribute.ScopeEE;
import javax.servlet.ServletRequest;

/**
 * Since the parent tag is not available from included JSP pages, the current
 * content type and validator is maintained as a request attribute.
 * These are updated for each of the nested tag levels.
 *
 * @author  AO Industries, Inc.
 */
// TODO: Reset this on ao-servlet-subrequest sub-requests?
//       Or done as a registrable subrequest event?  (Remove self from subrequest attributes OnSubrequest)
//       Or should this just be reset of SemantiCCMS page captures only?
//       Basically, how do we know when in a new page, and the old tag context is not actually what we want?
// Java 9: Make module-private
public class RequestEncodingContext {

	private static final ScopeEE.Request.Attribute<RequestEncodingContext> CURRENT_CONTEXT_REQUEST_ATTRIBUTE =
		ScopeEE.REQUEST.attribute(RequestEncodingContext.class.getName() + ".currentContext");

	// Java 9: Make module-private
	public static RequestEncodingContext getCurrentContext(ServletRequest request) {
		return CURRENT_CONTEXT_REQUEST_ATTRIBUTE.context(request).get();
	}

	// Java 9: Make module-private
	public static void setCurrentContext(ServletRequest request, RequestEncodingContext context) {
		CURRENT_CONTEXT_REQUEST_ATTRIBUTE.context(request).set(context);
	}

	/**
	 * A context that performs no validation and discards all output.
	 */
	// Java 9: Make module-private
	public static final RequestEncodingContext DISCARD = new RequestEncodingContext(
		MediaType.TEXT,
		new ValidMediaInput() {
			private final MediaValidator textValidator = MediaValidator.getMediaValidator(MediaType.TEXT, NullWriter.getInstance());
			{
				assert !(textValidator instanceof BufferedValidator) : "If were " + BufferedValidator.class.getName() + " could not share singleton";
			}
			@Override
			public MediaType getValidMediaInputType() {
				assert textValidator.getValidMediaInputType() == MediaType.TEXT;
				return MediaType.TEXT;
			}

			@Override
			public boolean isValidatingMediaInputType(MediaType inputType) {
				return textValidator.isValidatingMediaInputType(inputType);
			}

			@Override
			public boolean canSkipValidation(MediaType outputType) {
				return textValidator.isValidatingMediaInputType(outputType);
			}
		}
	);

	/**
	 * The content type that is currently be written.
	 */
	// Java 9: Make module-private
	public final MediaType contentType;

	/**
	 * The validator that is ensuring the data being written is valid for the current
	 * outputType.
	 */
	// Java 9: Make module-private
	public final ValidMediaInput validMediaInput;

	// Java 9: Make module-private
	public RequestEncodingContext(MediaType contentType, ValidMediaInput validMediaInput) {
		this.contentType = NullArgumentException.checkNotNull(contentType, "contentType");
		this.validMediaInput = NullArgumentException.checkNotNull(validMediaInput, "validMediaInput");
	}
}
