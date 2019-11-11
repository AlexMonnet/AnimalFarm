package com.logicgate.farm.service;

import com.logicgate.farm.domain.Animal;
import com.logicgate.farm.domain.Barn;
import com.logicgate.farm.domain.Color;
import com.logicgate.farm.repository.AnimalRepository;
import com.logicgate.farm.repository.BarnRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.stream.Collectors;

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

    final Color barnColor = animal.getFavoriteColor();
    final List<Barn> barns = barnRepository.findByColor(barnColor);
    final List<Animal> animals = animalRepository.findByFavoriteColor(barnColor);


    animalRepository.saveAndFlush(animal);
    if(barns.size() == 0){
      barns.add(barnRepository.saveAndFlush(new Barn("Barn " + barnColor.toString() + " 0", barnColor)));
    }
    animal.setBarn(barns.get(0));

    animals.add(animal);
    redistributeAnimalsOfBarnColor(barnColor, barns, animals);
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
   * This method redistributes all animals into barns.
   * @param barnColor The color of barns to redistribute the animals into
   */
  private void redistributeAnimalsOfBarnColor(final Color barnColor) {
    final List<Barn> barns = barnRepository.findByColor(barnColor);
    final List<Animal> animals = animalRepository.findByFavoriteColor(barnColor);

    redistributeAnimalsOfBarnColor(barnColor, barns, animals);
  }

    /**
   * This method redistributes all animals into barns.
   * @param barnColor The color of barns to redistribute the animals into
   * @param barns The list of barns with the same color as barnColor
   * @param animals The list of animals with the same favorite color as barnColor
   */
  private void redistributeAnimalsOfBarnColor(final Color barnColor, final List<Barn> barns, final List<Animal> animals) {
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
      final Map<Barn, List<Animal>> barnToAnimalRelationMap = animals.stream()
      .collect(Collectors.groupingBy(Animal::getBarn));
      final int animalsPerBarn = animals.size() / necessaryNumberOfBarns;
      final int remainderAnimals = animals.size() % necessaryNumberOfBarns;
      List<Animal> animalsToRehome = Collections.synchronizedList(new ArrayList<>());

      //Adjust the number of barns
      while (barnToAnimalRelationMap.size() < necessaryNumberOfBarns){
        barnToAnimalRelationMap.put(barnRepository.saveAndFlush(new Barn("Barn " + barnColor.toString(), barnColor)), new ArrayList<>());
      }

      if (barnToAnimalRelationMap.size() > necessaryNumberOfBarns){
        List<Barn> barnsToDelete = new ArrayList<>(barnToAnimalRelationMap.keySet()).subList(necessaryNumberOfBarns, barnToAnimalRelationMap.size());
        barnsToDelete.forEach(barn -> {
            barnRepository.delete(barn);
            animalsToRehome.addAll(barnToAnimalRelationMap.get(barn));
            barnToAnimalRelationMap.remove(barn);
          });
      }

      //Once the proper number of barns are established, we redistribute the animals.
      List<Barn> barnsWithTooManyAnimals = barnToAnimalRelationMap.entrySet().stream().filter((entry) -> { return (entry.getValue().size() > animalsPerBarn); }).map(entry -> entry.getKey()).collect(Collectors.toList());
      List<Barn> barnsWithTooFewAnimals  = barnToAnimalRelationMap.entrySet().stream().filter((entry) -> { return (entry.getValue().size() < animalsPerBarn); }).map(entry -> entry.getKey()).collect(Collectors.toList());

      for(Barn barn : barnsWithTooManyAnimals){
          final List<Animal> animalsInBarn = barnToAnimalRelationMap.get(barn);
          final List<Animal> overflowAnimals = animalsInBarn.subList(animalsPerBarn, animalsInBarn.size());
          animalsToRehome.addAll(overflowAnimals);
      }

      for(Barn barn : barnsWithTooFewAnimals){
          final List<Animal> animalsInBarn = barnToAnimalRelationMap.get(barn);
          final List<Animal> newAnimals = animalsToRehome.subList(0, animalsPerBarn - animalsInBarn.size());
          newAnimals.forEach(animal -> { animal.setBarn(barn); });
          animalsInBarn.addAll(newAnimals);
          //Save all of the animals added to this barn in this foreach
          animalRepository.saveAll(newAnimals);
          animalsToRehome.removeAll(newAnimals);
      }

      List<Barn> barnsForRemainingAnimals = new ArrayList<>(barnToAnimalRelationMap.keySet()).subList(0, remainderAnimals);
      for(Barn barn : barnsForRemainingAnimals){
          animalsToRehome.get(barnsForRemainingAnimals.indexOf(barn)).setBarn(barn);
      }

      //Save the animals that got a home due to being remainders.
      animalRepository.saveAll(animalsToRehome);
    }
  }

  /**
   * This method returns the minimum number of barns required to house
   * all of the animals.
   * @param barnCapacity The number of animals that can be housed in a barn
   * @param numberOfAnimals The number of animals to house
   * @return The minimum number of barns to house all animals within the capacity of each barn
   */
  private int findNecessaryNumberOfBarns(final int barnCapacity, final int numberOfAnimals) {
    int numberOfBarns = numberOfAnimals / barnCapacity;
    int remainderOfAnimals = numberOfAnimals % barnCapacity;
    if (remainderOfAnimals > 0)
    {
      numberOfBarns++;
    }

    return numberOfBarns;
  }

  /**
  * This method returns a boolean indicating whether or not
  *  the animals need to be redistributed amongst the barns
  * @param barns
  * @return
  */
 private boolean doBarnsNeedToBeBalanced(List<Barn> barns, List<Animal> animals) {
   boolean doBarnsNeedToBeBalanced = false;
   if(barns.size() == 0 && animals.size() != 0) {
    doBarnsNeedToBeBalanced = true;
   } else {
    final int barnCapacity = barns.get(0).getCapacity();
    final Map<Barn, List<Animal>> barnToAnimalMap = animals.stream().collect(Collectors.groupingBy(Animal::getBarn));

    final Integer minNumberOfAnimalsInABarn = barnToAnimalMap.values().stream().mapToInt(animalList -> animalList.size()).min().orElse(0);
    final Integer maxNumberOfAnimalsInABarn = barnToAnimalMap.values().stream().mapToInt(animalList -> animalList.size()).max().orElse(Integer.MAX_VALUE);
    final Integer sumNumberOfAnimalsInABarn = barnToAnimalMap.values().stream().mapToInt(animalList -> animalList.size()).sum();
    final Integer freeSpaceInAllBarns       = (barnCapacity * barnToAnimalMap.size()) - sumNumberOfAnimalsInABarn;

    doBarnsNeedToBeBalanced = (maxNumberOfAnimalsInABarn - minNumberOfAnimalsInABarn > 1 ||
                                maxNumberOfAnimalsInABarn > barnCapacity ||
                                freeSpaceInAllBarns >= barnCapacity);
   }

   return doBarnsNeedToBeBalanced;
 }


}
