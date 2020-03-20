package com.strikready.sandbox;

import com.strikready.sandbox.models.Organization;
import com.strikready.sandbox.models.Role;
import com.strikready.sandbox.models.User;
import com.strikready.sandbox.repositories.OrganizationRepository;
import com.strikready.sandbox.repositories.RoleRepository;
import com.strikready.sandbox.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.HashSet;

@SpringBootApplication
public class SpringAngularAuthApplication {
	@Autowired
	private UserRepository userRepository;

	public static void main(String[] args) {
		SpringApplication.run(SpringAngularAuthApplication.class, args);
	}


	@Bean
	CommandLineRunner init(RoleRepository roleRepository, OrganizationRepository organizationRepository) {

		return args -> {

			Role adminRole = roleRepository.findByRole("Admin");
			if (adminRole == null) {
				Role newAdminRole = new Role();
				newAdminRole.setRole("Admin");
				roleRepository.save(newAdminRole);
			}

			Role userRole = roleRepository.findByRole("User");
			if (userRole == null) {
				Role newUserRole = new Role();
				newUserRole.setRole("User");
				roleRepository.save(newUserRole);
			}

			Organization organization = organizationRepository.findByDomain("admin@sandbox.com");
			if (organization == null) {
				Organization adminorganization = new Organization();
				adminorganization.setDomain("admin@sandbox.com");
				adminorganization.setName("Sandbox");
				organizationRepository.save(adminorganization);
			}

			User user=userRepository.findByEmail("admin@sandbox.com");
			if(user==null) {
				user=new User();
				user.setEmail("admin@sandbox.com");
				user.setPassword("password");
				user.setEnabled(true);
				adminRole = roleRepository.findByRole("Admin");
				organization = organizationRepository.findByDomain("admin@sandbox.com");
				user.setRoles(new HashSet<>(Arrays.asList(adminRole)));
				user.setOrganizations(new HashSet<>(Arrays.asList(organization)));
				userRepository.save(user);
			}
		};

	}
}
