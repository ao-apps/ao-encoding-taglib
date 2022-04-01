/*
 * ao-encoding-taglib - High performance streaming character encoding in a JSP environment.
 * Copyright (C) 2012, 2013, 2016, 2017, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoapps.encoding.EncodingContext;
import com.aoapps.encoding.MediaEncoder;
import com.aoapps.encoding.MediaType;
import com.aoapps.encoding.MediaValidator;
import com.aoapps.encoding.MediaWriter;
import com.aoapps.encoding.servlet.EncodingContextEE;
import com.aoapps.lang.io.NullWriter;
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
import javax.servlet.jsp.tagext.SimpleTagSupport;

/**
 * Automatically encodes its output based on tag context while discarding all
 * content.  When the direct output of the body will not be used, this will
 * increase efficiency by discarding all write operations immediately.
 *
 * @author  AO Industries, Inc.
 */
public abstract class EncodingNullTag extends SimpleTagSupport {

	private static final Logger logger = Logger.getLogger(EncodingNullTag.class.getName());

	/**
	 * Gets the output type of this tag.  This is used to determine the correct
	 * encoder.  If the tag never has any output this should return {@code null}.
	 * When {@code null} is returned, any output will result in an error.
	 */
	public abstract MediaType getOutputType();

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
		// The output type cannot be determined until the body of the tag is invoked, because nested tags may
		// alter the resulting type.  We invoke the body first to accommodate nested tags.

		JspFragment body = getJspBody();
		if(body != null) {
			RequestEncodingContext.setCurrentContext(
				request,
				RequestEncodingContext.DISCARD
			);
			try {
				invoke(body);
			} finally {
				// Restore previous encoding context that is used for our output
				RequestEncodingContext.setCurrentContext(request, parentEncodingContext);
			}
		}

		MediaType newOutputType = getOutputType();
		if(newOutputType == null) {
			// No output, error if anything written.
			// prefix skipped
			doTag(FailOnWriteWriter.getInstance());
			// suffix skipped
		} else {
			final HttpServletResponse response = (HttpServletResponse)pageContext.getResponse();
			final JspWriter out = pageContext.getOut();

			// Determine the container's content type and validator
			final MediaType containerType;
			final Writer containerValidator;
			final boolean isNewContainerValidator;
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
				isNewContainerValidator = false;
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
				isNewContainerValidator = true;
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
				// Encode our output.  The encoder guarantees valid output for our parent.
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
					writeEncoderSuffix(mediaEncoder, out, newOutputType.getTrimBuffer());
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
						validator.validate(newOutputType.getTrimBuffer());
					} finally {
						RequestEncodingContext.setCurrentContext(request, parentEncodingContext);
					}
				}
			}

			// Write any suffix
			writeSuffix(containerType, containerValidator);
			if(isNewContainerValidator) {
				((MediaValidator)containerValidator).validate(containerType.getTrimBuffer());
			}
		}
	}

	/**
	 * Invokes the body.  This is only called when a body exists.  Subclasses may override this to perform
	 * actions before and/or after invoking the body.  Any overriding implementation should call
	 * super.invoke(JspFragment,MediaValidator) to invoke the body, unless it wants to suppress the body invocation.
	 * <p>
	 * The {@link RequestEncodingContext} has been set to {@link RequestEncodingContext#DISCARD} because no validation
	 * of the content is necessary as the output is discarded.  This means nested tags that attempt to produce valid
	 * output will not be limited by the parent encoding context of this tag.
	 * </p>
	 * <p>
	 * This implementation invokes {@link JspFragment#invoke(java.io.Writer)}
	 * while discarding all nested output.
	 * </p>
	 */
	protected void invoke(JspFragment body) throws JspException, IOException {
		body.invoke(NullWriter.getInstance());
	}

	/**
	 * <p>
	 * Writes any prefix in the container's media type.
	 * The output must be valid for the provided type.
	 * This will not be called when the output type is {@code null}.
	 * </p>
	 * <p>
	 * This default implementation prints nothing.
	 * </p>
	 *
	 * @see  #getOutputType()
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
		// Do nothing
	}

	protected void writeEncoderPrefix(MediaEncoder mediaEncoder, JspWriter out) throws JspException, IOException {
		mediaEncoder.writePrefixTo(out);
	}

	/**
	 * Once the out {@link JspWriter} has been replaced to output the proper content
	 * type, this version of {@link #doTag()} is called.
	 * <p>
	 * The body, if present, has already been invoked and any output discarded.
	 * </p>
	 * <p>
	 * This default implementation does nothing.
	 * </p>
	 */
	@SuppressWarnings("NoopMethodInAbstractClass")
	protected void doTag(Writer out) throws JspException, IOException {
		// Do nothing by default
	}

	protected void writeEncoderSuffix(MediaEncoder mediaEncoder, JspWriter out, boolean trim) throws JspException, IOException {
		mediaEncoder.writeSuffixTo(out, trim);
	}

	/**
	 * <p>
	 * Writes any suffix in the container's media type.
	 * The output must be valid for the provided type.
	 * This will not be called when the output type is {@code null}.
	 * </p>
	 * <p>
	 * This default implementation prints nothing.
	 * </p>
	 *
	 * @see  #getOutputType()
	 */
	@SuppressWarnings("NoopMethodInAbstractClass")
	protected void writeSuffix(MediaType containerType, Writer out) throws JspException, IOException {
		// By default, nothing is printed.
	}
}
