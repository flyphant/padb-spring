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
	
	//IOC容器
	private Map<String, Object> ioc=new HashMap<String, Object>();

	//定义一个HandlerMapping
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
		
		//6.等待请求阶段
//		String url=req.getRequestURI();
//		String contextPath=req.getContextPath();
//		
//		//多个斜杠换成一个斜杠
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
		//1.加载配置文件
		System.out.println("=====padb-spring init====");	
		doLoadConfig(config.getInitParameter("contextConfigLocation"));
		
		//2.扫描相关类
		doScanner(contextConfig.getProperty("scanPackage"));
		
		//3.初始化所有相关类,初始化到IOC容器中
		doInstance();
		
		//4.执行依赖注入(把加了@Autowired注解的字段赋值)
		doAutowired();
		
		//============Spring的核心功能IOC、DI已完成======
		
		//5.构造HandlerMapping,将URL和Method进行关联
		initHandlerMapper();
		
		//6.等待请求阶段
		
	}
	
	private void doDispatch(HttpServletRequest req,HttpServletResponse rsp){
		
		try {
			Handler handler=getHandler(req);
			
			if(handler==null){
				//如果没有匹配上,则返回404错误
				rsp.getWriter().write("404 not found");
				return;
			}
			
			//获取方法的参数列表
			Class<?>[] paramTypes=handler.method.getParameterTypes();
			
			//保存所有需要自动赋值的参数值
			Object[] paramValues=new Object[paramTypes.length];
			
			Map<String,String[]> params=req.getParameterMap();
			for(Map.Entry<String, String[]> param:params.entrySet()){
				String value=Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", "");
				
				//如果找到匹配对象，则开始填充参数值
				if(!handler.paramIndexMapping.containsKey(param.getKey())){
					continue;
				}
				Integer index=handler.paramIndexMapping.get(param.getKey());
				paramValues[index]=convert(paramTypes[index],value);
			}
			
			//设置方法中的request和response对象
			int reqIndex=handler.paramIndexMapping.get(HttpServletRequest.class.getName());
			paramValues[reqIndex]=req;
			
			int rspIndex=handler.paramIndexMapping.get(HttpServletResponse.class.getName());
			paramValues[rspIndex]=rsp;
			
			//利用反射方法执行目标方法
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
				
				//如果没有匹配上,则继续下一个匹配
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
			//注入:把所有的IOC容器中加了@Autowired注解的字段赋值
			Field[] fields=entry.getValue().getClass().getDeclaredFields();
			for(Field field:fields){
				//判断是否加了@autowired注解
				if(field.isAnnotationPresent(PadbAutowired.class)){
					continue;
				}
				
				PadbAutowired autowired=field.getAnnotation(PadbAutowired.class);
				String beanName=autowired.value().trim();
						
				if("".equals(beanName)){
					beanName=field.getType().getName();
				}
				
				try {
					//如果这个字段是私有的话,那么强制访问
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
		
		//ioc的key规则:
		//1.默认类名首字母小写
		//2.自定义命名,优先使用自定义命名
		//3.自动类型匹配
		
		try {
			for(String className:classNames){
				Class<?> clazz=Class.forName(className);
				
				//根据注解来初始化类
				if(clazz.isAnnotationPresent(PadbController.class)){
					Object instance=clazz.newInstance();
					String beanName=lowerFirstCase(clazz.getSimpleName());
					ioc.put(beanName, instance);
				}else if(clazz.isAnnotationPresent(PadbService.class)){
					
					PadbService padbService=clazz.getAnnotation(PadbService.class);
					
					//如果有定义service注解的名称,优先使用
					String beanName=padbService.value();
					
					if("".equals(beanName.trim())){
						//如果没有自定义名称,就使用类的默认首字母小写命名实例
						beanName=lowerFirstCase(clazz.getSimpleName());
					}
					Object instance=clazz.newInstance();
					ioc.put(beanName, instance);
					
					//自动类型匹配(将实现类赋值给接口)
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
	 * 首字母转换为小写
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
		
		protected Object controller;	//保存方法时对应的实例
		protected Method method;	//保存映射的方法
		protected Pattern pattern;	//RequestMapping存的URL的正则
		protected Map<String, Integer> paramIndexMapping;
		
		protected Handler(Object controller, Method method, Pattern pattern) {

			this.controller = controller;
			this.method = method;
			this.pattern = pattern;

			paramIndexMapping=new HashMap<String, Integer>();
			putParamIndexMapping(method);
		}
		
		private void putParamIndexMapping(Method method){
			
			//提取方法中加了注解的参数
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
			
			//提取方法中的request和response参数
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
