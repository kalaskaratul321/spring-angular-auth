package com.strikready.sandbox.controllers;

import static org.springframework.http.ResponseEntity.ok;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.strikready.sandbox.models.Organization;
import com.strikready.sandbox.models.Role;
import com.strikready.sandbox.models.User;
import com.strikready.sandbox.repositories.UserRepository;
import com.strikready.sandbox.services.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.strikready.sandbox.configs.JwtTokenProvider;


@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	@Autowired
	AuthenticationManager authenticationManager;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@Autowired
	UserRepository users;

	@Autowired
	private CustomUserDetailsService userService;

	@SuppressWarnings("rawtypes")
	@PostMapping("/login")
	public ResponseEntity login(@RequestBody AuthBody data) {
		try {
			String username = data.getEmail();
			authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, data.getPassword()));
			User user= this.users.findByEmail(username);
			String token = jwtTokenProvider.createToken(username, this.users.findByEmail(username).getRoles());
			Set<Role> role=user.getRoles();
			Set<Organization> organizations=user.getOrganizations();
			Map<Object, Object> model = new HashMap<>();
			model.put("username", username);
			model.put("token", token);
			model.put("role", role.iterator().next().getRole());
			model.put("organizations", organizations.iterator().next().getName());
			model.put("firstname", user.getFirstName());
			model.put("lastname", user.getLastName());
			return ok(model);
		} catch (AuthenticationException e) {
			throw new BadCredentialsException("Invalid email/password supplied");
		}
	}

	@SuppressWarnings("rawtypes")
	@PostMapping("/register")
	public ResponseEntity register(@RequestBody User user) {
		User userExists = userService.findUserByEmail(user.getEmail());
		if (userExists != null) {
			throw new BadCredentialsException("User with username: " + user.getEmail() + " already exists");
		}
		userService.saveUser(user);
		Map<Object, Object> model = new HashMap<>();
		model.put("message", "User registered successfully");
		return ok(model);
	}

	@SuppressWarnings("rawtypes")
	@PostMapping("/signout")
	public ResponseEntity signout() {
		Map<Object, Object> model = new HashMap<>();
		model.put("message", "User log out successfully");
		return ok(model);
	}
}