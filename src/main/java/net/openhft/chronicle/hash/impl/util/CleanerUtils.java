/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.hash.impl.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiFunction;
import net.openhft.chronicle.core.Jvm;

public class CleanerUtils {

	private static final BiFunction<Object, Runnable, Cleaner> function;

	static {
		function = Jvm.isJava26Plus() ? init26() : Jvm.isJava9Plus() ? init9() : init8();
	}

  public static Cleaner createCleaner(Object ob, Runnable thunk) {
  		return function.apply(ob, thunk);
  }

	static BiFunction<Object, Runnable, Cleaner> init26() {
		try {
			Class<?> cleanerClass = Class.forName("java.lang.ref.Cleaner");
			Method create = cleanerClass.getDeclaredMethod("create", Object.class, Runnable.class);
			Jvm.setAccessible(create);
			Object cleanerInstance = create.invoke(null);
			Method createCleanable = cleanerClass.getDeclaredMethod("register", Object.class, Runnable.class);
			Method clean = cleanerClass.getDeclaredMethod("clean");
			Jvm.setAccessible(create);
			return new CleanFunction26(cleanerInstance, createCleanable, clean);
		} catch (Exception e) {
			Jvm.error().on(CleanerUtils.class, "Unable to initialise CleanerUtils", e);
			throw new RuntimeException(e);
		}
	}

	static BiFunction<Object, Runnable, Cleaner> init8() {
		return init("sun.misc.Cleaner");
	}

	static BiFunction<Object, Runnable, Cleaner> init9() {
		return init("jdk.internal.ref.Cleaner");
	}

	static BiFunction<Object, Runnable, Cleaner> init(String classname) {
		try {
			Class<?> cleanerClass = Class.forName(classname);
			Method create = cleanerClass.getDeclaredMethod("create", Object.class, Runnable.class);
			Jvm.setAccessible(create);
			Method clean = cleanerClass.getDeclaredMethod("clean");
			Jvm.setAccessible(create);
			return new CleanFunction25(create, clean);
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			Jvm.error().on(CleanerUtils.class, "Unable to initialise CleanerUtils", e);
			throw new RuntimeException(e);
		}
	}

	private static final class CleanFunction25 implements BiFunction<Object, Runnable, Cleaner> {

		private final Method create;
		private final Method clean;
		public CleanFunction25(Method create, Method clean) {
			this.create = create;
			this.clean = clean;
		}

		@Override
		public Cleaner apply(Object ob, Runnable thunk) {
			try {
				Object cleanerInstance = create.invoke(null, ob, thunk);
				return () -> doClean(cleanerInstance);
			} catch (Exception e) {
				Jvm.warn().on(CleanerUtils.class, "Failed to create cleaner", e);
				throw new RuntimeException(e);
			}
		}

		protected final void doClean(Object cleanerInstance) {
			try {
				clean.invoke(cleanerInstance);
			} catch (IllegalAccessException | InvocationTargetException e) {
				Jvm.warn().on(CleanerUtils.class, "Failed to clean buffer", e);
			}
		}
	}

	private static final class CleanFunction26 implements BiFunction<Object, Runnable, Cleaner> {

		private final Object cleaner;
		private final Method createCleanable;
		private final Method doClean;

		public CleanFunction26(Object cleaner, Method createCleanable, Method doClean) {
			this.cleaner = cleaner;
			this.createCleanable = createCleanable;
			this.doClean = doClean;
		}

		@Override
		public Cleaner apply(Object ob, Runnable thunk) {
			try {
				// create the java.lang.ref.Cleaner$Cleanable
				Object cleanable = createCleanable.invoke(cleaner, ob, thunk);
				return new Cleanable(cleanable, doClean);
			} catch (Exception e) {
				Jvm.warn().on(CleanerUtils.class, "Failed to create cleaner", e);
				throw new RuntimeException(e);
			}
		}
	}
	private static final class Cleanable implements Cleaner {

		private final Object cleanable;
		private final Method doClean;

		public Cleanable(Object cleanable, Method doClean) {
			this.cleanable = cleanable;
			this.doClean = doClean;
		}

		@Override
		public void clean() {
			try {
				doClean.invoke(cleanable);
			} catch (IllegalAccessException | InvocationTargetException e) {
				Jvm.warn().on(CleanerUtils.class, "Failed to clean buffer", e);
			}
		}

	}
}
