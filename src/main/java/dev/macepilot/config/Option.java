package dev.macepilot.config;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Option {
    String category();
    String label();
    String tip() default "";
    double min() default 0;
    double max() default 100;
    double step() default 1;
}

