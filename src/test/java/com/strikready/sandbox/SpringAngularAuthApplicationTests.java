package com.strikready.sandbox;

import com.strikready.sandbox.models.Organization;
import com.strikready.sandbox.models.Role;
import com.strikready.sandbox.models.User;
import com.strikready.sandbox.repositories.OrganizationRepository;
import com.strikready.sandbox.repositories.RoleRepository;
import com.strikready.sandbox.repositories.UserRepository;
import com.strikready.sandbox.services.CustomUserDetailsService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.HashSet;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SpringAngularAuthApplicationTests {
	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private CustomUserDetailsService customUserDetailsService;

	@Test
	public void contextLoads() {
			/*try{
			User user=new User();
			user.setEmail("passwor1d@slb.com");
			user.setPhone("99707072163");
			user.setPassword("password");
			user.setFirstname("Sunil1");
			user.setLastname("Torkad");
			user.setOrgname("SLB");
			user.setEnabled(false);

			 customUserDetailsService.saveUser(user);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
*/

	}

}
