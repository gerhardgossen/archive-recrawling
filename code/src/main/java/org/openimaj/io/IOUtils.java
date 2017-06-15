/**
 * Copyright (c) 2011, The University of Southampton and the individual contributors.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   * 	Redistributions of source code must retain the above copyright notice,
 * 	this list of conditions and the following disclaimer.
 *
 *   *	Redistributions in binary form must reproduce the above copyright notice,
 * 	this list of conditions and the following disclaimer in the documentation
 * 	and/or other materials provided with the distribution.
 *
 *   *	Neither the name of the University of Southampton nor the names of its
 * 	contributors may be used to endorse or promote products derived from this
 * 	software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openimaj.io;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Arrays;

import org.openimaj.text.nlp.language.LanguageModel;

/**
 * Methods for reading Readable objects and writing Writeable objects.
 *
 * @author Jonathon Hare (jsh2@ecs.soton.ac.uk)
 * @author Sina Samangooei (ss@ecs.soton.ac.uk)
 */
public class IOUtils {
	/**
	 * Create a new instance of the given class. The class must have a no-args
	 * constructor. The constructor doesn't have to be public.
	 *
	 * @param <T>
	 *            The type of object.
	 * @param cls
	 *            The class.
	 * @return a new instance.
	 */
	public static <T extends InternalReadable> T newInstance(Class<T> cls) {
		try {
			return cls.newInstance();
		} catch (final Exception e) {
			try {
				final Constructor<T> constr = cls.getDeclaredConstructor();

				if (constr != null) {
					constr.setAccessible(true);
					return constr.newInstance();
				}
			} catch (final Exception e1) {
				throw new RuntimeException(e);
			}

			throw new RuntimeException(e);
		}
	}

    /**
     * Create a new instance of the given class. The class must have a no-args
     * constructor. The constructor doesn't have to be public.
     *
     * @param <T>
     *            The type of object.
     * @param className
     *            The class name.
     * @return a new instance.
     */
    @SuppressWarnings("unchecked")
    static <T extends InternalReadable> T newInstance(String className) {
        try {
            return newInstance(((Class<T>) Class.forName(className)));
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

	/**
	 * Read an instance of an object from an input stream. The stream is tested
	 * to contain the ASCII or binary header and the appropriate read instance
	 * is called.
	 *
	 * @param <T>
	 *            instance type expected
	 * @param fis
	 *            the input stream
	 * @param obj
	 *            the object to instantiate
	 * @return the object
	 *
	 * @throws IOException
	 *             if there is a problem reading the stream from the file
	 */
    public static <T extends LanguageModel> T read(InputStream fis, T obj) throws IOException {
		final BufferedInputStream bis = new BufferedInputStream(fis);
		if (isBinary(bis, ((ReadableBinary) obj).binaryHeader())) {
			final byte[] header = new byte[((ReadableBinary) obj).binaryHeader().length];
			bis.read(header, 0, header.length);
			((ReadableBinary) obj).readBinary(new DataInputStream(bis));
			return obj;
		} else {
		    throw new UnsupportedOperationException();
		}
	}

    /**
	 * Checks whether a given input stream contains readable binary information
	 * by checking for the first header.length bytes == header. The stream is
	 * reset to the beginning of the header once checked.
	 *
	 * @param bis
	 *            stream containing data
	 * @param header
	 *            expected binary header
	 * @return does the stream contain binary information
	 * @throws IOException
	 *             problem reading or reseting the stream
	 */
	private static boolean isBinary(BufferedInputStream bis, byte[] header) throws IOException {
		bis.mark(header.length + 10);
		final byte[] aheader = new byte[header.length];
		bis.read(aheader, 0, aheader.length);
		bis.reset();

		return Arrays.equals(aheader, header);
	}

}
