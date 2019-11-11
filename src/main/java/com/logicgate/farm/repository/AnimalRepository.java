package com.logicgate.farm.repository;

import com.logicgate.farm.domain.Animal;
import com.logicgate.farm.domain.Color;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnimalRepository extends JpaRepository<Animal, Long> {

  /**
   * This method finds all animals that have a favorite color equal to
   *  the parameter favoriteColor. This method is filled out using JPA
   *  Query creation
   * @param favoriteColor Animals' favorite color
   * @return List of animals with a favorite color of favoriteColor
   */
  public List<Animal> findByFavoriteColor(Color favoriteColor);
}
