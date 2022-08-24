/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.util;

import java.util.function.Consumer;
import java.util.function.Function;

public class FunctionalUtils {

    private FunctionalUtils() {}

    public static <T> Consumer<T> throwingConsumerWrapper(
            ThrowingConsumer<T, Exception> throwingConsumer) {
        return i -> {
            try {
                throwingConsumer.accept(i);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static <T, R> Function<T, R> throwingMapperWrapper(
            ThrowingMapper<T, R, Exception> throwingConsumer,
            R defaultValue
    ) {
        return i -> {
            R value = defaultValue;
            try {
                value = throwingConsumer.accept(i);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return value;
        };
    }

}
