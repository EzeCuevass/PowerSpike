package com.cuevas.powerspike;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class holamundo {
    @GetMapping("/")
    public String hola(){
        return "Hola mundo!";
    }
}
