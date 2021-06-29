/*
 * ao-encoding-taglib - High performance streaming character encoding in a JSP environment.
 * Copyright (C) 2012, 2016, 2017, 2019, 2020, 2021  AO Industries, Inc.
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

import com.aoapps.lang.i18n.Resources;
import com.aoapps.lang.io.LocalizedIOException;
import java.io.IOException;
import java.io.Writer;
import java.util.ResourceBundle;

// TODO: Move to ao-hodgepodge (and eventually a separate ao-io/ao-io-utils)?
// Java 9: Make module-private
final public class FailOnWriteWriter extends Writer {

	private static final Resources RESOURCES = Resources.getResources(ResourceBundle::getBundle, FailOnWriteWriter.class);

	private static final FailOnWriteWriter instance = new FailOnWriteWriter();

	// Java 9: Make module-private
	public static FailOnWriteWriter getInstance() {
		return instance;
	}

	private FailOnWriteWriter() {
	}

	@Override
	public void write(int c) throws IOException {
		throw new LocalizedIOException(RESOURCES, "noOutputAllowed");
	}

	@Override
	public void write(char cbuf[]) throws IOException {
		throw new LocalizedIOException(RESOURCES, "noOutputAllowed");
	}

	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		throw new LocalizedIOException(RESOURCES, "noOutputAllowed");
	}

	@Override
	public void write(String str) throws IOException {
		throw new LocalizedIOException(RESOURCES, "noOutputAllowed");
	}

	@Override
	public void write(String str, int off, int len) throws IOException {
		throw new LocalizedIOException(RESOURCES, "noOutputAllowed");
	}

	@Override
	public FailOnWriteWriter append(CharSequence csq) throws IOException {
		throw new LocalizedIOException(RESOURCES, "noOutputAllowed");
	}

	@Override
	public FailOnWriteWriter append(CharSequence csq, int start, int end) throws IOException {
		throw new LocalizedIOException(RESOURCES, "noOutputAllowed");
	}

	@Override
	public FailOnWriteWriter append(char c) throws IOException {
		throw new LocalizedIOException(RESOURCES, "noOutputAllowed");
	}

	@Override
	public void flush() {
		// Do nothing
	}

	@Override
	public void close() {
		// Do nothing
	}
}
