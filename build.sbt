
// ********************************************
// The basics
// ********************************************
name := "Piper"

organization := "molmed"

version := "v1.2.0-beta24-solid"

scalaVersion := "2.10.4"
// ********************************************
// Tests
// ********************************************
Seq(testNGSettings:_*)

testNGSuites := Seq("src/test/resources/testng.xml")
