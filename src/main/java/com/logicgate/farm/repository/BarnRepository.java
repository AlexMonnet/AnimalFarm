package com.logicgate.farm.repository;

import com.logicgate.farm.domain.Barn;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BarnRepository extends JpaRepository<Barn, Long> {

  // additional methods can be defined here

}
