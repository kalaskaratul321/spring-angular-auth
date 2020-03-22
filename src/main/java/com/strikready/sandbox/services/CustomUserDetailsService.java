package com.strikready.sandbox.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.strikready.sandbox.models.Organization;
import com.strikready.sandbox.models.Role;
import com.strikready.sandbox.models.User;
import com.strikready.sandbox.repositories.OrganizationRepository;
import com.strikready.sandbox.repositories.RoleRepository;
import com.strikready.sandbox.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

	@Autowired
	private UserRepository userRepository;
	
	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private OrganizationRepository organizationRepository;
	
	@Autowired
	private PasswordEncoder bCryptPasswordEncoder;
	
	public User findUserByEmail(String email) {
	    return userRepository.findByEmail(email);
	}
	
	public void saveUser(User user) {
	    user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
	    user.setEnabled(false);
	    Role userRole = roleRepository.findByRole("User");
		String userEmail=user.getEmail();
		String domain = userEmail.substring(userEmail .indexOf("@") + 1);
		Organization organization = organizationRepository.findByDomain(domain);
		if(organization==null){
			organization=new Organization();
			organization.setName(user.getOrgName());
			organization.setDomain(domain);
			organizationRepository.save(organization);
		}
		user.setOrganizations(new HashSet<>(Arrays.asList(organization)));
	    user.setRoles(new HashSet<>(Arrays.asList(userRole)));
	    userRepository.save(user);
	}

	public void savAdmin(User user) {
		user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
		userRepository.save(user);
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

	    User user = userRepository.findByEmail(email);
	    if(user != null) {
	        List<GrantedAuthority> authorities = getUserAuthority(user.getRoles());
	        return buildUserForAuthentication(user, authorities);
	    } else {
	        throw new UsernameNotFoundException("username not found");
	    }
	}
	
	private List<GrantedAuthority> getUserAuthority(Set<Role> userRoles) {
	    Set<GrantedAuthority> roles = new HashSet<>();
	    userRoles.forEach((role) -> {
	        roles.add(new SimpleGrantedAuthority(role.getRole()));
	    });

	    List<GrantedAuthority> grantedAuthorities = new ArrayList<>(roles);
	    return grantedAuthorities;
	}
	
	private UserDetails buildUserForAuthentication(User user, List<GrantedAuthority> authorities) {
	    return new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(), authorities);
	}
}