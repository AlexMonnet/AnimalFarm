package com.logicgate.farm.service;

import com.logicgate.farm.domain.Animal;
import com.logicgate.farm.domain.Barn;
import com.logicgate.farm.domain.Color;
import com.logicgate.farm.repository.AnimalRepository;
import com.logicgate.farm.repository.BarnRepository;
import com.zaxxer.hikari.util.ConcurrentBag;

import org.h2.mvstore.ConcurrentArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.RuntimeErrorException;

@Service
@Transactional
public class AnimalServiceImpl implements AnimalService {

  private final AnimalRepository animalRepository;

  private final BarnRepository barnRepository;

  @Autowired
  public AnimalServiceImpl(AnimalRepository animalRepository, BarnRepository barnRepository) {
    this.animalRepository = animalRepository;
    this.barnRepository = barnRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Animal> findAll() {
    return animalRepository.findAll();
  }

  @Override
  public void deleteAll() {
    animalRepository.deleteAll();
  }

  @Override
  public Animal addToFarm(Animal animal) {
    // List<Barn> barns = barnRepository.findByColor(animal.getFavoriteColor());
    // Color barnColor = animal.getFavoriteColor();
    // if(barns.size() == 0){
    //  barns.add(barnRepository.saveAndFlush(new Barn("Barn " + barnColor.toString(), barnColor)));
    // }
    // animal.setBarn(barns.get(0));
    animalRepository.saveAndFlush(animal);
    redistributeAnimalsOfBarnColor(animal.getFavoriteColor());
    return animal;
  }

  @Override
  public void addToFarm(List<Animal> animals) {
    animals.forEach(this::addToFarm);
  }

  @Override
  public void removeFromFarm(Animal animal) {
    animalRepository.delete(animal);
    redistributeAnimalsOfBarnColor(animal.getFavoriteColor());
  }

  @Override
  public void removeFromFarm(List<Animal> animals) {
    animals.forEach(animal -> removeFromFarm(animalRepository.getOne(animal.getId())));
  }

  /**
   * This method redistributes all animals into barns
   * @param barnColor
   */
  private void redistributeAnimalsOfBarnColor(Color barnColor) {
    final List<Barn> barns = barnRepository.findByColor(barnColor);
    final List<Animal> animals = animalRepository.findByFavoriteColor(barnColor);

    //If there are no animals, clear out the barns
    if(animals.size() == 0){
      barnRepository.deleteAll(barns);
    } else {
      //If we don't have any barns, go ahead and create one so that we can pull the barn capacity property.
      if(barns.size() == 0){
        barns.add(barnRepository.saveAndFlush(new Barn("Barn " + barnColor.toString() + " 0", barnColor)));
      }

      final int barnCapacity = barns.get(0).getCapacity();
      final int necessaryNumberOfBarns = findNecessaryNumberOfBarns(barnCapacity, animals.size());

      //Adjust the number of barns to fit the minimum necessary
      final List<Barn> availableBarns = adjustNumberOfBarns(necessaryNumberOfBarns, barns, barnColor);

      //Once the proper number of barns are established, we redistribute the animals.
      int iterator = 0;
      for (Animal animal : animals){
        int barnToAddTo = iterator % necessaryNumberOfBarns;
        Barn animalsBarn = availableBarns.get(barnToAddTo);
        animal.setBarn(animalsBarn);
        iterator++;
      }

      //Once the animals are put into new barns, remove any unused ones.
    }
  }

  /**
   * This method adjsuts the current number of barns to match the number
   *  specified via the parameter numberOfBarns
   * @param numberOfBarns number of barns to scale to
   * @return Newly adjusted list of Barns
   */
  private List<Barn> adjustNumberOfBarns(final int numberOfBarns, List<Barn> existingBarns, Color barnColor){
    final List<Barn> adjustedBarnList = new ArrayList<>(existingBarns);
    while (adjustedBarnList.size() < numberOfBarns){
      adjustedBarnList.add(barnRepository.saveAndFlush(new Barn("Barn " + barnColor.toString(), barnColor)));
    }

    if (adjustedBarnList.size() > numberOfBarns){
      adjustedBarnList.sort((Barn b1, Barn b2) -> b1.getName().compareTo(b2.getName()));
      adjustedBarnList.subList(numberOfBarns, adjustedBarnList.size()).forEach(barnRepository::delete);
    }

    return adjustedBarnList;
  }

  private int findNecessaryNumberOfBarns(final int barnCapacity, final int numberOfAnimals) {
    int numberOfBarns = numberOfAnimals / barnCapacity;

    //If there was any remainder from the integer division, bump the number of barns by 1
    if (numberOfAnimals % barnCapacity != 0)
    {
      numberOfBarns++;
    }
    return numberOfBarns;
  }
}
