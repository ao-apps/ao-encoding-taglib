/*
 * ao-encoding-taglib - High performance streaming character encoding in a JSP environment.
 * Copyright (C) 2020  AO Industries, Inc.
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
package com.aoindustries.encoding.taglib.legacy;

import com.aoindustries.encoding.Coercion;
import com.aoindustries.encoding.EncodingContext;
import com.aoindustries.encoding.MediaEncoder;
import com.aoindustries.encoding.MediaType;
import com.aoindustries.encoding.MediaValidator;
import com.aoindustries.encoding.MediaWriter;
import com.aoindustries.encoding.servlet.EncodingContextEE;
import com.aoindustries.encoding.taglib.EncodingBufferedTag;
import com.aoindustries.encoding.taglib.FailOnWriteWriter;
import com.aoindustries.encoding.taglib.RequestEncodingContext;
import com.aoindustries.io.buffer.AutoTempFileWriter;
import com.aoindustries.io.buffer.BufferResult;
import com.aoindustries.io.buffer.BufferWriter;
import com.aoindustries.servlet.jsp.LocalizedJspTagException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.BodyTagSupport;
import javax.servlet.jsp.tagext.TryCatchFinally;

/**
 * <p>
 * The exhibits all of the behavior of {@link EncodingFilteredBodyTag} with
 * the only exception being that it buffers its content instead of using filters.
 * This allows the tag to capture its body.  Character validation is performed
 * as the data goes into the buffer to ensure the captured data is correct for
 * its content type.
 * </p>
 * <p>
 * The tag also has the addition of a separate output type.  Thus, we have three
 * types involved:
 * </p>
 * <ol>
 * <li>contentType - The characters are validated to this type as they go into the buffer.</li>
 * <li>outputType - Our output characters are validated to this type as they are written.</li>
 * <li>containerType - Our output characters are encoded to this type as they are written.</li>
 * </ol>
 *
 * @author  AO Industries, Inc.
 */
public abstract class EncodingBufferedBodyTag extends BodyTagSupport implements TryCatchFinally {

	private static final Logger logger = Logger.getLogger(EncodingBufferedBodyTag.class.getName());

	public EncodingBufferedBodyTag() {
		init();
	}

	/**
	 * Gets the type of data that is contained by this tag.
	 */
	public abstract MediaType getContentType();

	/**
	 * Gets the output type of this tag.  This is used to determine the correct
	 * encoder.  If the tag never has any output this should return {@code null}.
	 * When {@code null} is returned, any output will result in an error.
	 */
	public abstract MediaType getOutputType();

	/**
	 * Gets the number of characters that may be buffered before switching to the
	 * use of a temp file.
	 *
	 * @return the threshold or {@link Long#MAX_VALUE} to never use temp files.
	 *
	 * @see  AutoTempFileWriter#DEFAULT_TEMP_FILE_THRESHOLD
	 */
	public long getTempFileThreshold() {
		return AutoTempFileWriter.DEFAULT_TEMP_FILE_THRESHOLD;
	}

	private static final long serialVersionUID = 1L;

	// Set in doStartTag
	private transient RequestEncodingContext parentEncodingContext;
	private transient MediaType containerType;
	private transient Writer containerValidator;
	private transient boolean writePrefixSuffix;
	// Set in updateValidatingOut
	private transient MediaType validatingOutputType;
	private transient MediaEncoder mediaEncoder;
	private transient RequestEncodingContext validatingOutEncodingContext;
	private transient Writer validatingOut;
	// Set in initCapture
	private transient BufferWriter captureBuffer;
	private transient MediaValidator captureValidator;
	private transient boolean bodyUnbuffered;

	private void init() {
		parentEncodingContext = null;
		containerType = null;
		containerValidator = null;
		writePrefixSuffix = false;
		validatingOutputType = null;
		mediaEncoder = null;
		validatingOutEncodingContext = null;
		validatingOut = null;
		captureBuffer = null;
		captureValidator = null;
		bodyUnbuffered = false;
	}

	/**
	 * @deprecated  You should probably be implementing in {@link #doStartTag(java.io.Writer)}
	 *
	 * @see  #doStartTag(java.io.Writer)
	 */
	@Deprecated
	@Override
	public int doStartTag() throws JspException {
		try {
			final ServletRequest request = pageContext.getRequest();
			final JspWriter out = pageContext.getOut();

			parentEncodingContext = RequestEncodingContext.getCurrentContext(request);

			// Determine the container's content type and validator
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
				final ServletResponse response = pageContext.getResponse();
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
			MediaType newOutputType = getOutputType();
			writePrefixSuffix = (newOutputType != null);
			if(writePrefixSuffix) writePrefix(containerType, containerValidator);

			updateValidatingOut(pageContext.getOut(), newOutputType);
			RequestEncodingContext.setCurrentContext(request, validatingOutEncodingContext);
			return checkStartTagReturn(doStartTag(validatingOut));
		} catch(IOException e) {
			throw new JspTagException(e);
		}
	}

	/**
	 * Sets or replaces the validating out variables based on the current {@linkplain #getOutputType() output type}.
	 * When the output type changes, which can happen during body invocation, the validating variables will be updated.
	 */
	private void updateValidatingOut(JspWriter out, MediaType newOutputType) throws JspException, IOException {
		if(validatingOut == null || newOutputType != validatingOutputType) {
			final MediaEncoder newMediaEncoder;
			final RequestEncodingContext newValidatingOutEncodingContext;
			final Writer newValidatingOut;
			if(newOutputType == null) {
				// No output, error if anything written.
				newMediaEncoder = null;
				// prefix skipped
				newValidatingOutEncodingContext = parentEncodingContext;
				newValidatingOut = FailOnWriteWriter.getInstance();
				// suffix skipped
			} else {
				final HttpServletRequest request = (HttpServletRequest)pageContext.getRequest();
				final HttpServletResponse response = (HttpServletResponse)pageContext.getResponse();
				// Find the encoder
				EncodingContext encodingContext = new EncodingContextEE(pageContext.getServletContext(), request, response);
				newMediaEncoder = MediaEncoder.getInstance(encodingContext, newOutputType, containerType);
				if(newMediaEncoder != null) {
					if(logger.isLoggable(Level.FINER)) {
						logger.finer("Using MediaEncoder: " + newMediaEncoder);
					}
					logger.finest("Setting encoder options");
					setMediaEncoderOptions(newMediaEncoder);
					// Encode our output.  The encoder guarantees valid output for our parent.
					logger.finest("Writing encoder prefix");
					writeEncoderPrefix(newMediaEncoder, out); // TODO: Skip prefix and suffix when empty?
					MediaWriter mediaWriter = new MediaWriter(encodingContext, newMediaEncoder, out);
					newValidatingOutEncodingContext = new RequestEncodingContext(newOutputType, mediaWriter);
					newValidatingOut = mediaWriter;
				} else {
					// If parentValidMediaInput exists and is validating our output type, no additional validation is required
					if(
						parentEncodingContext != null
						&& parentEncodingContext.validMediaInput.isValidatingMediaInputType(newOutputType)
					) {
						if(logger.isLoggable(Level.FINER)) {
							logger.finer("Passing-through with validating parent: " + parentEncodingContext.validMediaInput);
						}
						newValidatingOutEncodingContext = new RequestEncodingContext(newOutputType, parentEncodingContext.validMediaInput);
						newValidatingOut = out;
					} else {
						// Not using an encoder and parent doesn't validate our output, validate our own output.
						MediaValidator validator = MediaValidator.getMediaValidator(newOutputType, out);
						if(logger.isLoggable(Level.FINER)) {
							logger.finer("Using MediaValidator: " + validator);
						}
						newValidatingOutEncodingContext = new RequestEncodingContext(newOutputType, validator);
						newValidatingOut = validator;
					}
				}
			}
			if(validatingOut != null) {
				if(logger.isLoggable(Level.FINER)) {
					logger.finer(
						"Changing output type from "
						+ validatingOutputType + " to "
						+ newOutputType
					);
				}
			}
			validatingOutputType = newOutputType;
			mediaEncoder = newMediaEncoder;
			validatingOutEncodingContext = newValidatingOutEncodingContext;
			validatingOut = newValidatingOut;
		}
	}

	/**
	 * Once the out {@link JspWriter} has been replaced to output the proper content
	 * type, this version of {@link #doStartTag()} is called.
	 *
	 * @param  out  the output.  If passed-through, this will be a {@link JspWriter}
	 *
	 * @return  Must return either {@link #EVAL_BODY_BUFFERED} (the default) or {@link #SKIP_BODY}
	 */
	protected int doStartTag(Writer out) throws JspException, IOException {
		return EVAL_BODY_BUFFERED;
	}

	private static int checkStartTagReturn(int startTagReturn) throws JspTagException {
		if(startTagReturn == EVAL_BODY_BUFFERED) {
			return EVAL_BODY_BUFFERED;
		}
		if(startTagReturn == SKIP_BODY) {
			return SKIP_BODY;
		}
		throw new LocalizedJspTagException(
			ApplicationResources.accessor,
			"EncodingBufferedBodyTag.checkStartTagReturn.invalid",
			startTagReturn
		);
	}

	/**
	 * Replaces the captureBuffer, preparing for the next invocation of doBody, attempts to
	 * {@linkplain BodyTagUtils#unbuffer(javax.servlet.jsp.tagext.BodyContent, java.io.Writer) unbuffer} with direct
	 * access to the current {@link #captureValidator}.
	 * <p>
	 * Sets {@link #bodyUnbuffered} to {@code true} when successfully directly performing capture.
	 * Otherwise, {@link #bodyUnbuffered} is {@code false} when the body content continues to use default buffering.
	 * </p>
	 */
	private void initCapture() throws JspTagException, UnsupportedEncodingException {
		assert captureBuffer == null;
		assert captureValidator == null;
		ServletRequest request = pageContext.getRequest();
		captureBuffer = EncodingBufferedTag.newBufferWriter(request, getTempFileThreshold());
		final MediaType myContentType = getContentType();
		captureValidator = MediaValidator.getMediaValidator(myContentType, captureBuffer);
		RequestEncodingContext.setCurrentContext(
			request,
			new RequestEncodingContext(myContentType, captureValidator)
		);
		bodyUnbuffered = BodyTagUtils.unbuffer(bodyContent, captureValidator);
	}

	/**
	 * <p>
	 * The only way to replace the "out" variable in the generated JSP is to use
	 * {@link #EVAL_BODY_BUFFERED}.  Without this, any writer given to {@link PageContext#pushBody(java.io.Writer)}
	 * is not used.  We want to both use {@linkplain BufferWriter our own buffer implementation} as well as validate the
	 * data on-the-fly.
	 * </p>
	 * <p>
	 * To workaround this issue, this very hackily replaces the writer field directly on the
	 * <code>BodyContentImpl</code>.  When unable to replace the field, falls back to using
	 * the standard buffering (much less desirable).
	 * </p>
	 * <p>
	 * This is similar to the direct field access performed by {@link Coercion}.
	 * </p>
	 */
	@Override
	public void doInitBody() throws JspTagException {
		try {
			initCapture();
		} catch(UnsupportedEncodingException e) {
			throw new JspTagException(e);
		}
	}

	/**
	 * @deprecated  You should probably be implementing in {@link #doAfterBody(com.aoindustries.io.buffer.BufferResult, java.io.Writer)}
	 *
	 * @see  #doAfterBody(com.aoindustries.io.buffer.BufferResult, java.io.Writer)
	 */
	@Deprecated
	@Override
	public int doAfterBody() throws JspException {
		try {
			if(!bodyUnbuffered) {
				if(logger.isLoggable(Level.FINER)) {
					int charCount = bodyContent.getBufferSize() - bodyContent.getRemaining();
					logger.finer("Validating " + charCount + " buffered " + (charCount == 1 ? "character" : "characters"));
				}
				bodyContent.writeOut(captureValidator);
				bodyContent.clear();
			}
			captureValidator.flush();
			captureBuffer.close();
			final BufferResult capturedBody = captureBuffer.getResult();
			captureBuffer = null;
			captureValidator = null;
			updateValidatingOut(bodyContent.getEnclosingWriter(), getOutputType());
			RequestEncodingContext.setCurrentContext(pageContext.getRequest(), validatingOutEncodingContext);
			int afterBodyReturn = BodyTagUtils.checkAfterBodyReturn(doAfterBody(capturedBody, validatingOut));
			if(afterBodyReturn == EVAL_BODY_AGAIN) {
				initCapture();
			}
			return afterBodyReturn;
		} catch(IOException e) {
			throw new JspTagException(e);
		}
	}

	/**
	 * While the out {@link JspWriter} is still replaced to output the proper content
	 * type, this version of {@link #doAfterBody()} is called.
	 *
	 * @param  out  the output.  If passed-through, this will be a {@link JspWriter}
	 *
	 * @return  Must return either {@link #SKIP_BODY} (the default) or {@link #EVAL_BODY_AGAIN}
	 */
	protected int doAfterBody(BufferResult capturedBody, Writer out) throws JspException, IOException {
		return SKIP_BODY;
	}

	/**
	 * @deprecated  You should probably be implementing in {@link #doEndTag(java.io.Writer)}
	 *
	 * @see  #doEndTag(java.io.Writer)
	 */
	@Deprecated
	@Override
	public int doEndTag() throws JspException {
		try {
			updateValidatingOut(pageContext.getOut(), getOutputType());
			RequestEncodingContext.setCurrentContext(pageContext.getRequest(), validatingOutEncodingContext);
			int endTagReturn = BodyTagUtils.checkEndTagReturn(doEndTag(validatingOut));
			if(mediaEncoder != null) {
				logger.finest("Writing encoder suffix");
				writeEncoderSuffix(mediaEncoder, pageContext.getOut());
			}

			// Write any suffix
			if(writePrefixSuffix) writeSuffix(containerType, containerValidator);

			return endTagReturn;
		} catch(IOException e) {
			throw new JspTagException(e);
		}
	}

	/**
	 * While the out {@link JspWriter} is still replaced to output the proper content
	 * type, this version of {@link #doEndTag()} is called.
	 *
	 * @param  out  the output.  If passed-through, this will be a {@link JspWriter}
	 *
	 * @return  Must return either {@link #EVAL_PAGE} (the default) or {@link #SKIP_PAGE}
	 */
	protected int doEndTag(Writer out) throws JspException, IOException {
		return EVAL_PAGE;
	}

	@Override
	public void doCatch(Throwable t) throws Throwable {
		throw t;
	}

	@Override
	public void doFinally() {
		try {
			// Restore previous encoding context that is used for our output
			RequestEncodingContext.setCurrentContext(pageContext.getRequest(), parentEncodingContext);
		} finally {
			init();
		}
	}

	/**
	 * <p>
	 * Writes any prefix in the container's media type.
	 * The output must be valid for the provided type.
	 * This will not be called when the initial output type is {@code null}.
	 * </p>
	 * <p>
	 * This default implementation prints nothing.
	 * </p>
	 *
	 * @see  #getOutputType()
	 */
	@SuppressWarnings("NoopMethodInAbstractClass")
	protected void writePrefix(MediaType containerType, Writer out) throws JspTagException, IOException {
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

	protected void writeEncoderSuffix(MediaEncoder mediaEncoder, JspWriter out) throws JspException, IOException {
		mediaEncoder.writeSuffixTo(out);
	}

	/**
	 * <p>
	 * Writes any suffix in the container's media type.
	 * The output must be valid for the provided type.
	 * This will not be called when the initial output type is {@code null}.
	 * </p>
	 * <p>
	 * This default implementation prints nothing.
	 * </p>
	 *
	 * @see  #getOutputType()
	 */
	@SuppressWarnings("NoopMethodInAbstractClass")
	protected void writeSuffix(MediaType containerType, Writer out) throws JspTagException, IOException {
		// By default, nothing is printed.
	}
}
