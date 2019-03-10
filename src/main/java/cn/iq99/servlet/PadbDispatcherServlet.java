package cn.iq99.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cn.iq99.annotation.PadbAutowired;
import cn.iq99.annotation.PadbController;
import cn.iq99.annotation.PadbRequestMapping;
import cn.iq99.annotation.PadbRequestParam;
import cn.iq99.annotation.PadbService;

public class PadbDispatcherServlet extends HttpServlet{

	private static final long serialVersionUID = 1L;
	
	private Properties contextConfig=new Properties();
	
	private List<String> classNames=new ArrayList<String>();
	
	//IOC����
	private Map<String, Object> ioc=new HashMap<String, Object>();

	//����һ��HandlerMapping
//	private Map<String, Method> handlerMapping=new HashMap<String, Method>();
	private List<Handler> handlerMapping=new ArrayList<PadbDispatcherServlet.Handler>();
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		this.doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse rsp)
			throws ServletException, IOException {
		
		//6.�ȴ�����׶�
//		String url=req.getRequestURI();
//		String contextPath=req.getContextPath();
//		
//		//���б�ܻ���һ��б��
//		url=url.replaceAll(contextPath, "").replaceAll("/+", "/");
//		if(!handlerMapping.containsKey(url)){
//			rsp.getWriter().write("404 not found");
//			return;
//		}
//		
//		Method method=handlerMapping.get(url);
//		System.out.println("methond:"+method);
//		
//		method.invoke(obj, args)
		doDispatch(req, rsp);
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		//1.���������ļ�
		System.out.println("=====padb-spring init====");	
		doLoadConfig(config.getInitParameter("contextConfigLocation"));
		
		//2.ɨ�������
		doScanner(contextConfig.getProperty("scanPackage"));
		
		//3.��ʼ�����������,��ʼ����IOC������
		doInstance();
		
		//4.ִ������ע��(�Ѽ���@Autowiredע����ֶθ�ֵ)
		doAutowired();
		
		//============Spring�ĺ��Ĺ���IOC��DI�����======
		
		//5.����HandlerMapping,��URL��Method���й���
		initHandlerMapper();
		
		//6.�ȴ�����׶�
		
	}
	
	private void doDispatch(HttpServletRequest req,HttpServletResponse rsp){
		
		try {
			Handler handler=getHandler(req);
			
			if(handler==null){
				//���û��ƥ����,�򷵻�404����
				rsp.getWriter().write("404 not found");
				return;
			}
			
			//��ȡ�����Ĳ����б�
			Class<?>[] paramTypes=handler.method.getParameterTypes();
			
			//����������Ҫ�Զ���ֵ�Ĳ���ֵ
			Object[] paramValues=new Object[paramTypes.length];
			
			Map<String,String[]> params=req.getParameterMap();
			for(Map.Entry<String, String[]> param:params.entrySet()){
				String value=Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", "");
				
				//����ҵ�ƥ�������ʼ������ֵ
				if(!handler.paramIndexMapping.containsKey(param.getKey())){
					continue;
				}
				Integer index=handler.paramIndexMapping.get(param.getKey());
				paramValues[index]=convert(paramTypes[index],value);
			}
			
			//���÷����е�request��response����
			int reqIndex=handler.paramIndexMapping.get(HttpServletRequest.class.getName());
			paramValues[reqIndex]=req;
			
			int rspIndex=handler.paramIndexMapping.get(HttpServletResponse.class.getName());
			paramValues[rspIndex]=rsp;
			
			//���÷��䷽��ִ��Ŀ�귽��
			handler.method.invoke(handler.controller, paramValues);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Object convert(Class<?> clazz, String value) {
		if(Long.class==clazz){
			return Long.valueOf(value);
		}
		return value;
	}

	private Handler getHandler(HttpServletRequest req) {
		if(handlerMapping.isEmpty()){return null;}
		
		String url=req.getRequestURI();
		String contextPath=req.getContextPath();
		url=url.replaceAll(contextPath, "").replaceAll("/+", "/");
		
		for(Handler handler:handlerMapping){
			try {
				Matcher matcher=handler.pattern.matcher(url);
				
				//���û��ƥ����,�������һ��ƥ��
				if(!matcher.matches()){continue;}
				
				return handler;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	private void initHandlerMapper() {
		if(ioc.isEmpty()) return;
		
		for(Entry<String, Object> entry:ioc.entrySet()){
			Class<?> clazz=entry.getValue().getClass();
			
			if(!clazz.isAnnotationPresent(PadbController.class)){
				continue;
			}
			
			String baseUrl="";
			if(clazz.isAnnotationPresent(PadbRequestMapping.class)){
				PadbRequestMapping requestMapping=clazz.getAnnotation(PadbRequestMapping.class);
				baseUrl=requestMapping.value();
			}
			
			Method[] methods=clazz.getMethods();
			for(int i=0;i<methods.length;i++){
				Method method=methods[i];
				if(!method.isAnnotationPresent(PadbRequestMapping.class)){continue;}
				
				PadbRequestMapping requestMapping=method.getAnnotation(PadbRequestMapping.class);
				String regex=requestMapping.value();
				
				regex=(baseUrl+regex).replaceAll("/+", "/");
				Pattern pattern=Pattern.compile(regex);
				handlerMapping.add(new Handler(entry.getValue(), method, pattern));
				System.out.println("Mapping:"+regex+","+method);
			}
		}
		
	}

	private void doAutowired() {
		if(ioc.isEmpty()) return;
		
		for(Entry<String, Object> entry:ioc.entrySet()){
			//ע��:�����е�IOC�����м���@Autowiredע����ֶθ�ֵ
			Field[] fields=entry.getValue().getClass().getDeclaredFields();
			for(Field field:fields){
				//�ж��Ƿ����@autowiredע��
				if(field.isAnnotationPresent(PadbAutowired.class)){
					continue;
				}
				
				PadbAutowired autowired=field.getAnnotation(PadbAutowired.class);
				String beanName=autowired.value().trim();
						
				if("".equals(beanName)){
					beanName=field.getType().getName();
				}
				
				try {
					//�������ֶ���˽�еĻ�,��ôǿ�Ʒ���
					field.setAccessible(true);
					
					field.set(entry.getValue(), ioc.get(beanName));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void doInstance() {
		if(classNames.isEmpty()){
			return ;
		}
		
		//ioc��key����:
		//1.Ĭ����������ĸСд
		//2.�Զ�������,����ʹ���Զ�������
		//3.�Զ�����ƥ��
		
		try {
			for(String className:classNames){
				Class<?> clazz=Class.forName(className);
				
				//����ע������ʼ����
				if(clazz.isAnnotationPresent(PadbController.class)){
					Object instance=clazz.newInstance();
					String beanName=lowerFirstCase(clazz.getSimpleName());
					ioc.put(beanName, instance);
				}else if(clazz.isAnnotationPresent(PadbService.class)){
					
					PadbService padbService=clazz.getAnnotation(PadbService.class);
					
					//����ж���serviceע�������,����ʹ��
					String beanName=padbService.value();
					
					if("".equals(beanName.trim())){
						//���û���Զ�������,��ʹ�����Ĭ������ĸСд����ʵ��
						beanName=lowerFirstCase(clazz.getSimpleName());
					}
					Object instance=clazz.newInstance();
					ioc.put(beanName, instance);
					
					//�Զ�����ƥ��(��ʵ���ำֵ���ӿ�)
					Class<?>[] interfaces=clazz.getInterfaces();
					for(Class<?> i:interfaces){
						ioc.put(i.getName(), instance);
					}
				}else{
					continue;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	/**
	 * ����ĸת��ΪСд
	 * @param str
	 * @return
	 */
	private String lowerFirstCase(String str) {
		char[] chars=str.toCharArray();
		chars[0]+=32;
		return String.valueOf(chars);
	}

	private void doLoadConfig(String location){
		InputStream inputStream=this.getClass().getClassLoader().getResourceAsStream(location);
		try {
			contextConfig.load(inputStream);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally{
			if(null!=inputStream){
				try {
					inputStream.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	private void doScanner(String basePackage){
		
		System.out.println("basePackage:"+basePackage);
		URL url=this.getClass().getClassLoader().getResource("/"+basePackage.replaceAll("\\.", "/"));
		File dir=new File(url.getFile());
		
		for(File file:dir.listFiles()){
			if(file.isDirectory()){
				doScanner(basePackage+"."+file.getName());
			}else{
				String className=basePackage+"."+file.getName().replace(".class", "");
				classNames.add(className);
				System.out.println("class name:"+className);
			}
		}
	}
	
	private class Handler{
		
		protected Object controller;	//���淽��ʱ��Ӧ��ʵ��
		protected Method method;	//����ӳ��ķ���
		protected Pattern pattern;	//RequestMapping���URL������
		protected Map<String, Integer> paramIndexMapping;
		
		protected Handler(Object controller, Method method, Pattern pattern) {

			this.controller = controller;
			this.method = method;
			this.pattern = pattern;

			paramIndexMapping=new HashMap<String, Integer>();
			putParamIndexMapping(method);
		}
		
		private void putParamIndexMapping(Method method){
			
			//��ȡ�����м���ע��Ĳ���
			Annotation[][] pa=method.getParameterAnnotations();
			for(int i=0;i<pa.length;i++){
				for(Annotation annotation:pa[i]){
					if(annotation instanceof PadbRequestParam){
						String paramName=((PadbRequestParam) annotation).value();
						if(!"".equals(paramName.trim())){
							paramIndexMapping.put(paramName, i);
						}
					}
				}
			}
			
			//��ȡ�����е�request��response����
			Class<?>[] paramTypes=method.getParameterTypes();
			for(int i=0;i<paramTypes.length;i++){
				Class<?> type=paramTypes[i];
				if(type==HttpServletRequest.class || type==HttpServletResponse.class){
					
					paramIndexMapping.put(type.getName(), i);
				}
			}
		}
		
		private Handler getHandler(HttpServletRequest req) throws Exception{
			if(handlerMapping.isEmpty()){return null;}
			
			String url=req.getRequestURI();
			String contextPath=req.getContextPath();
			url=url.replace(contextPath,"").replaceAll("/+", "/");
			
			for(Handler handler:handlerMapping){
				try {
					Matcher matcher=handler.pattern.matcher(url);
					if(!matcher.matches()){continue;}
					
					return handler;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return null;
		}
	}
}
