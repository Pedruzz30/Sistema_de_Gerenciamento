package controller;

import model.Permissao;
import repository.UsuarioRepository;
import service.RentabilidadeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rentabilidade")
public class RentabilidadeController {

    private final RentabilidadeService rentabilidadeService;
    private final UsuarioRepository usuarioRepository;

    public RentabilidadeController(RentabilidadeService rentabilidadeService,
                                   UsuarioRepository usuarioRepository) {
        this.rentabilidadeService = rentabilidadeService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/ranking")
    public ResponseEntity<RentabilidadeService.AnaliseRentabilidade> ranking(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.VER_FINANCAS,
                "Voce nao tem permissao para visualizar rentabilidade."
        );
        return ResponseEntity.ok(rentabilidadeService.gerarRanking());
    }
}
