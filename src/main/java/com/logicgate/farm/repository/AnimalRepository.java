package com.logicgate.farm.repository;

import com.logicgate.farm.domain.Animal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnimalRepository extends JpaRepository<Animal, Long> {

  // additional methods can be defined here

}
