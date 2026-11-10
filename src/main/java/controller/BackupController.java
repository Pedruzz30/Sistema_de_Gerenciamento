package controller;

import model.Permissao;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.BackupService;

@RestController
@RequestMapping("/api/sistema/backup")
public class BackupController {

    private final BackupService backupService;
    private final UsuarioRepository usuarioRepository;

    public BackupController(BackupService backupService, UsuarioRepository usuarioRepository) {
        this.backupService = backupService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public ResponseEntity<BackupService.Situacao> situacao(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        exigirPermissao(ruHeader);
        return ResponseEntity.ok(backupService.situacao());
    }

    @PostMapping
    public ResponseEntity<BackupService.Situacao> fazerAgora(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        exigirPermissao(ruHeader);
        if (!backupService.ativo()) {
            return ResponseEntity.badRequest().body(backupService.situacao());
        }
        boolean executou = backupService.executar("pedido na tela");
        return executou
                ? ResponseEntity.ok(backupService.situacao())
                : ResponseEntity.status(409).body(backupService.situacao());
    }

    private void exigirPermissao(String ruHeader) {
        ControllerUtils.resolveUserAndRequirePermission(
                ruHeader, usuarioRepository, Permissao.VER_FINANCAS,
                "Voce nao tem permissao para ver os backups.");
    }
}
