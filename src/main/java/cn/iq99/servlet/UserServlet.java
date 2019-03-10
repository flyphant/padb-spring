package cn.iq99.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class UserServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		req.setCharacterEncoding("utf-8");
		resp.setContentType("text/html;charset=utf-8");
		String username=req.getParameter("username");
		String password=req.getParameter("password");
		System.out.println("�û���:"+username+"  ����"+password);
		req.setAttribute("info", "��¼�ɹ���ʾ��Ϣ");
		req.getRequestDispatcher("index.jsp").forward(req, resp);
	}
	

}
