package controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EstoquePageController {

    @GetMapping("/estoque")
    public String abrirPaginaEstoque() {
        return "estoque";
    }
}