package com.logcopilot.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminUiController {

	@GetMapping({"/admin", "/admin/"})
	public String adminEntry() {
		return "forward:/admin/index.html";
	}
}
