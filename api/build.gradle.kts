import ca.stellardrift.permissionsex.gradle.setupPublication

setupPublication()

dependencies {
    compileOnlyApi("org.checkerframework:checker-qual:3.7.1")
    annotationProcessor("org.immutables:value:2.8.8")
    compileOnlyApi("org.immutables:value:2.8.8:annotations")
    compileOnlyApi("org.immutables:builder:2.8.8")
    api("io.projectreactor:reactor-core:3.4.0")
    api("org.pcollections:pcollections:3.1.4")
}