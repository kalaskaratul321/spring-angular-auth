package com.strikready.sandbox.repositories;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.strikready.sandbox.repositories.kg.CourtRepository;
import com.strikready.sandbox.repositories.kg.KgEdgeRepository;
import com.strikready.sandbox.repositories.kg.LegalCaseRepository;
import com.strikready.sandbox.repositories.kg.harvest.HarvestSourceRepository;
import com.strikready.sandbox.repositories.kg.harvest.HarvestTaskRepository;
import org.junit.Test;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.query.parser.PartTree;

/**
 * Parses every derived query method the way Spring Data does at startup, so a
 * method naming a property that does not exist fails here in milliseconds
 * instead of breaking the application context (which needs MongoDB to test).
 */
public class RepositoryQueryMethodsTest {

	private static final List<Class<?>> REPOSITORIES = Arrays.<Class<?>>asList(LegalCaseRepository.class,
			KgEdgeRepository.class, CourtRepository.class, HarvestSourceRepository.class, HarvestTaskRepository.class,
			UserRepository.class, RoleRepository.class, OrganizationRepository.class, ProductRepository.class);

	@Test
	public void allDerivedQueriesResolve() {
		List<String> failures = new ArrayList<>();
		for (Class<?> repo : REPOSITORIES) {
			Class<?> domain = (Class<?>) ((ParameterizedType) repo.getGenericInterfaces()[0]).getActualTypeArguments()[0];
			for (Method m : repo.getDeclaredMethods()) {
				if (isInheritedCrudMethod(m)) {
					continue;
				}
				try {
					new PartTree(m.getName(), domain);
				} catch (RuntimeException e) {
					failures.add(repo.getSimpleName() + "." + m.getName() + ": " + e.getMessage());
				}
			}
		}
		assertTrue(String.join("\n", failures), failures.isEmpty());
	}

	/** Overrides of MongoRepository/CrudRepository methods (e.g. delete) are not derived queries. */
	private static boolean isInheritedCrudMethod(Method m) {
		for (Method base : MongoRepository.class.getMethods()) {
			if (base.getName().equals(m.getName()) && base.getParameterCount() == m.getParameterCount()) {
				return true;
			}
		}
		return false;
	}
}
