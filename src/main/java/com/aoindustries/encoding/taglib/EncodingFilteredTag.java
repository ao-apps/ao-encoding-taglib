/*
 * ao-encoding-taglib - High performance streaming character encoding in a JSP environment.
 * Copyright (C) 2009, 2010, 2011, 2012, 2013, 2016, 2017, 2019, 2020  AO Industries, Inc.
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
package com.aoindustries.encoding.taglib;

import com.aoindustries.encoding.EncodingContext;
import com.aoindustries.encoding.MediaEncoder;
import com.aoindustries.encoding.MediaType;
import com.aoindustries.encoding.MediaValidator;
import com.aoindustries.encoding.MediaWriter;
import com.aoindustries.encoding.servlet.EncodingContextEE;
import java.io.IOException;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.JspFragment;
import javax.servlet.jsp.tagext.SimpleTag;
import javax.servlet.jsp.tagext.SimpleTagSupport;

/**
 * <p>
 * An implementation of {@link SimpleTag} that automatically validates its
 * content and automatically encodes its output correctly given its context.
 * It also validates its own output when used in a non-validating context.  For
 * higher performance, it filters the output from its body instead of buffering.
 * </p>
 * <p>
 * The content validation is primarily focused on making sure the contained data
 * is properly encoded.  This is to avoid data corruption or intermingling of
 * data and code.  It does not go through great lengths such as ensuring that
 * XHTML Strict is valid or JavaScript will run correctly.
 * </p>
 * <p>
 * In additional to checking that its contents are well behaved, it also is
 * well behaved for its container by properly encoding its output for its
 * context.  To determine its context, it uses the content type of the currently
 * registered {@link RequestEncodingContext} to perform proper encoding.
 * If it fails to find any such context, it uses the content type of the
 * {@link HttpServletResponse}.
 * </p>
 * <p>
 * Finally, if no existing {@link RequestEncodingContext} is found, this will
 * validate its own output against the content type of the
 * {@link HttpServletResponse} to make sure it is well-behaved.
 * </p>
 *
 * @author  AO Industries, Inc.
 */
public abstract class EncodingFilteredTag extends SimpleTagSupport {

	private static final Logger logger = Logger.getLogger(EncodingFilteredTag.class.getName());

	/**
	 * Gets the type of data that is contained by this tag.
	 * This is also the output type.
	 */
	public abstract MediaType getContentType();

	/**
	 * @deprecated  You should probably be implementing in {@link #doTag(java.io.Writer)}
	 *
	 * @see  #doTag(java.io.Writer)
	 */
	@Deprecated
	@Override
	public void doTag() throws JspException, IOException {
		final PageContext pageContext = (PageContext)getJspContext();
		final HttpServletRequest request = (HttpServletRequest)pageContext.getRequest();
		final RequestEncodingContext parentEncodingContext = RequestEncodingContext.getCurrentContext(request);
		final MediaType newOutputType = getContentType();
		final HttpServletResponse response = (HttpServletResponse)pageContext.getResponse();
		final JspWriter out = pageContext.getOut();

		// Determine the container's content type and validator
		final MediaType containerType;
		final Writer containerValidator;
		if(parentEncodingContext != null) {
			// Use the output type of the parent
			containerType = parentEncodingContext.contentType;
			if(logger.isLoggable(Level.FINER)) {
				logger.finer("containerType from parentEncodingContext: " + containerType);
			}
			assert parentEncodingContext.validMediaInput.isValidatingMediaInputType(containerType)
				: "It is a bug in the parent to not validate its input consistent with its content type";
			// Already validated
			containerValidator = out;
			if(logger.isLoggable(Level.FINER)) {
				logger.finer("containerValidator from parentEncodingContext: " + containerValidator);
			}
		} else {
			// Use the content type of the response
			String responseContentType = response.getContentType();
			// Default to XHTML: TODO: Is there a better way since can't set content type early in response then reset again...
			if(responseContentType == null) responseContentType = MediaType.XHTML.getContentType();
			containerType = MediaType.getMediaTypeForContentType(responseContentType);
			if(logger.isLoggable(Level.FINER)) {
				logger.finer("containerType from responseContentType: " + containerType + " from " + responseContentType);
			}
			// Need to add validator
			containerValidator = MediaValidator.getMediaValidator(containerType, out);
			if(logger.isLoggable(Level.FINER)) {
				logger.finer("containerValidator from containerType: " + containerValidator + " from " + containerType);
			}
		}

		// Write any prefix
		writePrefix(containerType, containerValidator);

		// Find the encoder
		EncodingContext encodingContext = new EncodingContextEE(pageContext.getServletContext(), request, response);
		MediaEncoder mediaEncoder = MediaEncoder.getInstance(encodingContext, newOutputType, containerType);
		if(mediaEncoder != null) {
			if(logger.isLoggable(Level.FINER)) {
				logger.finer("Using MediaEncoder: " + mediaEncoder);
			}
			logger.finest("Setting encoder options");
			setMediaEncoderOptions(mediaEncoder);
			// Encode both our output and the content.  The encoder validates our input and guarantees valid output for our parent.
			logger.finest("Writing encoder prefix");
			writeEncoderPrefix(mediaEncoder, out);
			try {
				MediaWriter mediaWriter = new MediaWriter(encodingContext, mediaEncoder, out);
				RequestEncodingContext.setCurrentContext(
					request,
					new RequestEncodingContext(newOutputType, mediaWriter)
				);
				try {
					doTag(mediaWriter);
				} finally {
					// Restore previous encoding context that is used for our output
					RequestEncodingContext.setCurrentContext(request, parentEncodingContext);
				}
			} finally {
				logger.finest("Writing encoder suffix");
				writeEncoderSuffix(mediaEncoder, out);
			}
		} else {
			// If parentValidMediaInput exists and is validating our output type, no additional validation is required
			if(
				parentEncodingContext != null
				&& parentEncodingContext.validMediaInput.isValidatingMediaInputType(newOutputType)
			) {
				if(logger.isLoggable(Level.FINER)) {
					logger.finer("Passing-through with validating parent: " + parentEncodingContext.validMediaInput);
				}
				RequestEncodingContext.setCurrentContext(
					request,
					new RequestEncodingContext(newOutputType, parentEncodingContext.validMediaInput)
				);
				try {
					doTag(out);
				} finally {
					RequestEncodingContext.setCurrentContext(request, parentEncodingContext);
				}
			} else {
				// Not using an encoder and parent doesn't validate our output, validate our own output.
				MediaValidator validator = MediaValidator.getMediaValidator(newOutputType, out);
				if(logger.isLoggable(Level.FINER)) {
					logger.finer("Using MediaValidator: " + validator);
				}
				RequestEncodingContext.setCurrentContext(
					request,
					new RequestEncodingContext(newOutputType, validator)
				);
				try {
					doTag(validator);
				} finally {
					RequestEncodingContext.setCurrentContext(request, parentEncodingContext);
				}
			}
		}

		// Write any suffix
		writeSuffix(containerType, containerValidator);
	}

	/**
	 * <p>
	 * Writes any prefix in the container's media type.
	 * The output must be valid for the provided type.
	 * </p>
	 * <p>
	 * This default implementation prints nothing.
	 * </p>
	 */
	@SuppressWarnings("NoopMethodInAbstractClass")
	protected void writePrefix(MediaType containerType, Writer out) throws JspException, IOException {
		// By default, nothing is printed.
	}

	/**
	 * Sets the media encoder options.  This is how subclass tag attributes
	 * can effect the encoding.
	 */
	@SuppressWarnings("NoopMethodInAbstractClass")
	protected void setMediaEncoderOptions(MediaEncoder mediaEncoder) {
	}

	protected void writeEncoderPrefix(MediaEncoder mediaEncoder, JspWriter out) throws JspException, IOException {
		mediaEncoder.writePrefixTo(out);
	}

	/**
	 * Once the out {@link JspWriter} has been replaced to output the proper content
	 * type, this version of {@link #doTag()} is called.
	 * <p>
	 * This implementation invokes {@link JspFragment#invoke(java.io.Writer)}
	 * of the JSP body, if present.
	 * </p>
	 *
	 * @param  out  the output.  If passed-through, this will be a {@link JspWriter}
	 */
	protected void doTag(Writer out) throws JspException, IOException {
		JspFragment body = getJspBody();
		if(body != null) {
			// Check for JspWriter to avoid a JspWriter wrapping a JspWriter
			body.invoke(
				(out instanceof JspWriter)
				? null
				: out
			);
		}
	}

	protected void writeEncoderSuffix(MediaEncoder mediaEncoder, JspWriter out) throws JspException, IOException {
		mediaEncoder.writeSuffixTo(out);
	}

	/**
	 * <p>
	 * Writes any suffix in the container's media type.
	 * The output must be valid for the provided type.
	 * </p>
	 * <p>
	 * This default implementation prints nothing.
	 * </p>
	 */
	@SuppressWarnings("NoopMethodInAbstractClass")
	protected void writeSuffix(MediaType containerType, Writer out) throws JspException, IOException {
		// By default, nothing is printed.
	}
}
