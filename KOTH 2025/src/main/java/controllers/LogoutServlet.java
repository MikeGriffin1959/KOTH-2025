package controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@Controller
public class LogoutServlet {

    @GetMapping("/LogoutServlet")
    public String doGet(HttpServletRequest request) {
        System.out.println("LogoutServlet.doGet() called");
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/LoginServlet";
    }
}
