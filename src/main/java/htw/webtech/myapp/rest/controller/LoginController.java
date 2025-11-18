package htw.webtech.myapp.rest.controller;

import htw.webtech.myapp.business.service.AuthService;
import htw.webtech.myapp.rest.model.LoginRequest;
import htw.webtech.myapp.rest.model.LoginResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:5173",
                       "https://frnawebfront2.onrender.com"
})
public class LoginController {

    private final AuthService authService;

    public LoginController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req) {
        String token = authService.authenticate(req.getEmail(), req.getPassword());
        if (token != null) {
            return ResponseEntity.ok(new LoginResponse(true, token, "ok"));
        } else {
            return ResponseEntity.status(401)
                    .body(new LoginResponse(false, null, "invalid credentials"));
        }
    }
}
