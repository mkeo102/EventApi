package dev.mkeo102.eventApi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventTarget {

    Priority priority();

    enum Priority {

        EXTREMELY_HIGH(3), HIGH(2), MEDIUM(1), LOWEST(0);

        Priority(int value){
            this.value =value;
        }

        final int value;

    }

}
