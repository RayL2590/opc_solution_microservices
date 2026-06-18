package com.medilabo.frontservice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Redirige "/" vers "/ui/patients". Préfixe /ui/ pour ne pas entrer en collision avec la route /patients du Gateway (G-1). */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/ui/patients";
    }
}
