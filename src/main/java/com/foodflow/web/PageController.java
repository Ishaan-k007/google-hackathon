package com.foodflow.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/supermarket")
    public String supermarket() {
        return "supermarket";
    }

    @GetMapping("/charity")
    public String charity() {
        return "charity";
    }

    @GetMapping("/driver")
    public String driver() {
        return "driver";
    }

    @GetMapping("/vision")
    public String vision() {
        return "vision";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }
}
