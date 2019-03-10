package cn.iq99.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.PARAMETER})    //ֻ���ڲ�����ʹ��
@Retention(RetentionPolicy.RUNTIME)	//����ʱ����
@Documented	//��ע�⽫������javadoc��
public @interface PadbRequestParam {

	String value() default "";
}
