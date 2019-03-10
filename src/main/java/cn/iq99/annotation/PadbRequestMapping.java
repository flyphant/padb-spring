package cn.iq99.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE,ElementType.METHOD})	//既可以在类上使用，也可以在方法上使用
@Retention(RetentionPolicy.RUNTIME)	//运行时解析
@Documented	//该注解将包含在javadoc中
public @interface PadbRequestMapping {

	String value() default "";
}
