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
package com.aoapps.encoding.taglib.legacy;

import com.aoapps.encoding.EncodingContext;
import com.aoapps.encoding.MediaEncoder;
import com.aoapps.encoding.MediaType;
import com.aoapps.encoding.MediaValidator;
import com.aoapps.encoding.MediaWriter;
import com.aoapps.encoding.servlet.EncodingContextEE;
import com.aoapps.encoding.taglib.RequestEncodingContext;
import com.aoapps.lang.i18n.Resources;
import com.aoapps.servlet.BodyContentImplCoercionOptimizerInitializer;
import com.aoapps.servlet.jsp.LocalizedJspTagException;
import java.io.IOException;
import java.io.Writer;
import java.util.ResourceBundle;
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
 * An implementation of {@link BodyTagSupport} that automatically validates its
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
public abstract class EncodingFilteredBodyTag extends BodyTagSupport implements TryCatchFinally {

	private static final Logger logger = Logger.getLogger(EncodingFilteredBodyTag.class.getName());

	private static final Resources RESOURCES = Resources.getResources(ResourceBundle::getBundle, EncodingFilteredBodyTag.class);

	/**
	 * Return value for {@link #doStartTag(java.io.Writer)}.  It will be converted
	 * to either {@link #EVAL_BODY_INCLUDE} or {@link #EVAL_BODY_BUFFERED}, as
	 * appropriate to the given filtering and validation.
	 */
	public static final int EVAL_BODY_FILTERED = 7;

	static {
		assert EVAL_BODY_FILTERED != SKIP_BODY;
		assert EVAL_BODY_FILTERED != EVAL_BODY_INCLUDE;
		assert EVAL_BODY_FILTERED != EVAL_BODY_BUFFERED;
	}

	protected EncodingFilteredBodyTag() {
		init();
	}

	/**
	 * Gets the type of data that is contained by this tag.
	 * This is also the output type.
	 */
	public abstract MediaType getContentType();

	private enum Mode {
		PASSTHROUGH(false),
		ENCODING(true),
		VALIDATING(true);

		private final boolean buffered;

		private Mode(boolean buffered) {
			this.buffered = buffered;
		}
	}

	private static final long serialVersionUID = 1L;

	// Set in doStartTag
	private transient RequestEncodingContext parentEncodingContext;
	private transient MediaType containerType;
	private transient Writer containerValidator;
	private transient boolean isNewContainerValidator;
	// Set in updateValidatingOut
	private transient JspWriter directOut;
	private transient MediaType validatingOutputType;
	private transient MediaEncoder mediaEncoder;
	private transient RequestEncodingContext validatingOutEncodingContext;
	private transient Writer validatingOut;
	private transient boolean isNewValidator;
	private transient Mode mode;
	// Set in doStartTag, possibly updated in initValidation
	private transient boolean bodyUnbuffered;

	private void init() {
		parentEncodingContext = null;
		containerType = null;
		containerValidator = null;
		isNewContainerValidator = false;
		directOut = null;
		validatingOutputType = null;
		mediaEncoder = null;
		validatingOutEncodingContext = null;
		validatingOut = null;
		isNewValidator = false;
		mode = null;
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
				isNewContainerValidator = false;
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
				isNewContainerValidator = true;
				if(logger.isLoggable(Level.FINER)) {
					logger.finer("containerValidator from containerType: " + containerValidator + " from " + containerType);
				}
			}

			// Write any prefix
			writePrefix(containerType, containerValidator);

			updateValidatingOut(out);
			bodyUnbuffered = !mode.buffered;
			RequestEncodingContext.setCurrentContext(request, validatingOutEncodingContext);
			return checkStartTagReturn(doStartTag(validatingOut), mode);
		} catch(IOException e) {
			throw new JspTagException(e);
		}
	}

	/**
	 * Sets or replaces the validating out variables based on the current {@linkplain #getContentType() output type}.
	 * When the output type changes, which can happen during body invocation, the validating variables will be updated.
	 */
	private void updateValidatingOut(JspWriter out) throws JspException, IOException {
		final MediaType newOutputType = getContentType();
		if(validatingOut == null || out != directOut || newOutputType != validatingOutputType) {
			final MediaEncoder newMediaEncoder;
			final RequestEncodingContext newValidatingOutEncodingContext;
			final Writer newValidatingOut;
			final boolean newIsNewValidator;
			final Mode newMode;
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
				// Encode both our output and the content.  The encoder validates our input and guarantees valid output for our parent.
				logger.finest("Writing encoder prefix");
				writeEncoderPrefix(newMediaEncoder, out);
				MediaWriter mediaWriter = new MediaWriter(encodingContext, newMediaEncoder, out);
				newValidatingOutEncodingContext = new RequestEncodingContext(newOutputType, mediaWriter);
				newValidatingOut = mediaWriter;
				newIsNewValidator = false;
				newMode = Mode.ENCODING;
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
					newIsNewValidator = false;
					newMode = Mode.PASSTHROUGH;
				} else {
					// Not using an encoder and parent doesn't validate our output, validate our own output.
					MediaValidator validator = MediaValidator.getMediaValidator(newOutputType, out);
					if(logger.isLoggable(Level.FINER)) {
						logger.finer("Using MediaValidator: " + validator);
					}
					newValidatingOutEncodingContext = new RequestEncodingContext(newOutputType, validator);
					newValidatingOut = validator;
					newIsNewValidator = true;
					newMode = Mode.VALIDATING;
				}
			}
			if(validatingOut != null) {
				if(logger.isLoggable(Level.FINER)) {
					logger.finer(
						"Changing output type from "
						+ validatingOutputType + " (mode " + mode + ") to "
						+ newOutputType + " (mode " + newMode + ")"
					);
				}
				if(isNewValidator) {
					((MediaValidator)validatingOut).validate(validatingOutputType.getTrimBuffer());
				}
				if(mode.buffered != newMode.buffered) {
					throw new LocalizedJspTagException(
						RESOURCES,
						"updateValidatingOut.incompatibleBufferingMode",
						validatingOutputType,
						mode,
						newOutputType,
						newMode
					);
				}
			}
			directOut = out;
			validatingOutputType = newOutputType;
			mediaEncoder = newMediaEncoder;
			validatingOutEncodingContext = newValidatingOutEncodingContext;
			validatingOut = newValidatingOut;
			isNewValidator = newIsNewValidator;
			mode = newMode;
		}
	}

	/**
	 * Once the out {@link JspWriter} has been replaced to output the proper content
	 * type, this version of {@link #doStartTag()} is called.
	 *
	 * @param  out  the output.  If passed-through, this will be a {@link JspWriter}
	 *
	 * @return  Must return either {@link #EVAL_BODY_FILTERED} (the default) or {@link #SKIP_BODY}
	 */
	protected int doStartTag(Writer out) throws JspException, IOException {
		return EVAL_BODY_FILTERED;
	}

	private static int checkStartTagReturn(int startTagReturn, Mode mode) throws JspTagException {
		if(startTagReturn == EVAL_BODY_FILTERED) {
			return mode.buffered ? EVAL_BODY_BUFFERED : EVAL_BODY_INCLUDE;
		}
		if(startTagReturn == SKIP_BODY) {
			return SKIP_BODY;
		}
		throw new LocalizedJspTagException(RESOURCES, "checkStartTagReturn.invalid", startTagReturn);
	}

	/**
	 * If the {@linkplain Mode#buffered current mode is buffered}, attempts to
	 * {@linkplain BodyTagUtils#unbuffer(javax.servlet.jsp.tagext.BodyContent, java.io.Writer) unbuffer} with direct
	 * access to the current {@link #validatingOut}.
	 * <p>
	 * Sets {@link #bodyUnbuffered} to {@code true} when successfully directly performing validation.
	 * Otherwise, {@link #bodyUnbuffered} is {@code false} when the body content continues to use default buffering.
	 * </p>
	 */
	private void initValidation() throws JspTagException {
		ServletRequest request = pageContext.getRequest();
		RequestEncodingContext.setCurrentContext(request, validatingOutEncodingContext);
		if(mode.buffered) {
			boolean alreadyUnbuffered = bodyUnbuffered;
			bodyUnbuffered = BodyTagUtils.unbuffer(bodyContent, validatingOut);
			if(alreadyUnbuffered && !bodyUnbuffered) {
				throw new AssertionError("If BodyContent can be unbuffered once, it must be able to be unbuffered again");
			}
		} else {
			assert bodyUnbuffered;
		}
	}

	/**
	 * <p>
	 * The only way to replace the "out" variable in the generated JSP is to use
	 * {@link #EVAL_BODY_BUFFERED}.  Without this, any writer given to {@link PageContext#pushBody(java.io.Writer)}
	 * is not used.  We don't actually want to buffer the content, but only want to filter and validate the
	 * data on-the-fly.
	 * </p>
	 * <p>
	 * To workaround this issue, this very hackily replaces the writer field directly on the
	 * <code>BodyContentImpl</code>.  When unable to replace the field, falls back to using
	 * the standard buffering (much less desirable).
	 * </p>
	 * <p>
	 * This is similar to the direct field access performed by {@link BodyContentImplCoercionOptimizerInitializer}.
	 * </p>
	 */
	@Override
	public void doInitBody() throws JspException {
		assert mode.buffered;
		assert !bodyUnbuffered;
		initValidation();
	}

	/**
	 * @deprecated  You should probably be implementing in {@link #doAfterBody(java.io.Writer)}
	 *
	 * @see  #doAfterBody(java.io.Writer)
	 */
	@Deprecated
	@Override
	public int doAfterBody() throws JspException {
		try {
			if(!bodyUnbuffered) {
				assert mode.buffered;
				if(logger.isLoggable(Level.FINER)) {
					int charCount = bodyContent.getBufferSize() - bodyContent.getRemaining();
					logger.finer((mode == Mode.ENCODING ? "Encoding" : "Validating ") + charCount + " buffered " + (charCount == 1 ? "character" : "characters"));
				}
				bodyContent.writeOut(validatingOut);
				bodyContent.clear();
			}
			updateValidatingOut(mode.buffered ? bodyContent.getEnclosingWriter() : pageContext.getOut());
			RequestEncodingContext.setCurrentContext(pageContext.getRequest(), validatingOutEncodingContext);
			int afterBodyReturn = BodyTagUtils.checkAfterBodyReturn(doAfterBody(validatingOut));
			if(afterBodyReturn == EVAL_BODY_AGAIN) {
				initValidation();
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
	protected int doAfterBody(Writer out) throws JspException, IOException {
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
			final JspWriter out = pageContext.getOut();
			updateValidatingOut(out);
			RequestEncodingContext.setCurrentContext(pageContext.getRequest(), validatingOutEncodingContext);
			int endTagReturn = doEndTag(validatingOut);
			if(isNewValidator) {
				((MediaValidator)validatingOut).validate(validatingOutputType.getTrimBuffer());
			}
			BodyTagUtils.checkEndTagReturn(endTagReturn);
			if(mediaEncoder != null) {
				logger.finest("Writing encoder suffix");
				writeEncoderSuffix(mediaEncoder, out, validatingOutputType.getTrimBuffer());
			}

			// Write any suffix
			writeSuffix(containerType, containerValidator);
			if(isNewContainerValidator) {
				((MediaValidator)containerValidator).validate();
			}

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
		// Do nothing
	}

	protected void writeEncoderPrefix(MediaEncoder mediaEncoder, JspWriter out) throws JspException, IOException {
		mediaEncoder.writePrefixTo(out);
	}

	protected void writeEncoderSuffix(MediaEncoder mediaEncoder, JspWriter out, boolean trim) throws JspException, IOException {
		mediaEncoder.writeSuffixTo(out, trim);
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
