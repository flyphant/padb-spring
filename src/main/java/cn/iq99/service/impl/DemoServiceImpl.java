package cn.iq99.service.impl;

import cn.iq99.annotation.PadbService;
import cn.iq99.service.DemoService;

@PadbService("demoService")
public class DemoServiceImpl implements DemoService {

	public String getName(String name) {
		// TODO Auto-generated method stub
		return "padb-spring "+name;
	}

}
