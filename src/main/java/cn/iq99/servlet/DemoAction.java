package cn.iq99.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cn.iq99.annotation.PadbAutowired;
import cn.iq99.annotation.PadbController;
import cn.iq99.annotation.PadbRequestMapping;
import cn.iq99.annotation.PadbRequestParam;
import cn.iq99.service.DemoService;

@PadbController
@PadbRequestMapping("/demo")
public class DemoAction {
	
	@PadbAutowired
	private DemoService demoService;
	
	@PadbRequestMapping("/query.json")
	public void query(HttpServletRequest req,HttpServletResponse rsp,@PadbRequestParam("padbName") String name){
		
		try {
			String result=demoService.getName(name);
			rsp.getWriter().write(result);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@PadbRequestMapping("/add.json")
	public void add(HttpServletRequest req,HttpServletResponse rsp,@PadbRequestParam("padbA") Integer a,@PadbRequestParam("padbB") Integer b){
		
		try {
			rsp.getWriter().write(a+"+"+b+"="+(a+b));
		} catch (Exception e) {
			// TODO: handle exception
		}
	}
}
