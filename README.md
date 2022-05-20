# SOMA

![](https://github.com/ease-crc/soma/workflows/Evaluation/badge.svg)
![](https://github.com/ease-crc/soma/workflows/Documentation/badge.svg)
![](https://github.com/ease-crc/soma/workflows/Deployment/badge.svg)

SOMA -- the **SO**cio-physical **M**odel of **A**ctivities -- is an ontological model of activities that attempts to equip robotic agents with abstract knowledge to make them perform activities in a more flexible and robust manner.
*Sôma* is also a word for *body* derived from Ancient Greek, and *body* being a central aspect in this model due to severe difficulties of controlling the body of an agent in the physical world.
More concretely, the scope of SOMA is how agents interact with their environment in terms of what are their intentions, what is their plan, how do they move, and how do they get into contact with each other or items in the environment.

SOMA is fully implemented in form of an OWL ontology which is based on the DOLCE+DnS Ultralite (DUL) upper-level ontology.
[DUL](http://www.ontologydesignpatterns.org/ont/dul/DUL.owl) is a carefully designed upper-level ontology that seeks to model general categories underlying human cognition without making any discipline-specific assumptions.
Our extensions mainly focus on characterizing different aspects of activities that were not considered sufficiently in DUL for the scope of autonomous robotics.

## Release Notes

### 2.0.0
*   BREAKING CHANGE: Removal of `IOLite`
    <details>
        <summary>Details</summary>
        <ul>
            <li>
                Reason: incompatible with <code>DUL-v32</code>
            </li>
            <li>
                Note: Some important concepts, e.g. <code>IOLite#DigitalResource</code>, are still referenced in the taxonomy for backwards-compatibility. If you are missing any crucial concepts/roles, let us know
            </li>
            <li>
                Ongoing effort to create a model of CRAM (see next point) will replace some of the IOLite taxonomy at some point
            </li>
        </ul>
    </details>
*   BREAKING CHANGE: Complete and still ongoing remodeling of `SOMA-IO`
    <details>
        <summary>Details</summary>
        <ul>
            <li>
                Reason: Was in a primitive state; we need a better model of IO stuff to model CRAM
            </li>
            <li>
                Note: Removal/resorting of various concepts and roles. To the best of our knowledge, these should not have been in use anyway - please let us know if you are missing anything
            </li>
        </ul>
    </details>
*   BREAKING CHANGE: New ontology in the import structure: `SOMA NEEM`
    <details>
        <summary>Details</summary>
        Some SOMA entities, e.g., <code>KynoDinamicData</code>, have been decided to be too specific for the basic SOMA ontology. These have been moved to <code>SOMA NEEM</code>, which is of course part of the collapsed SOMA version
    </details>
*   Feat: Added mising labels to various concepts and roles
*   Feat: Protégé-setup for offline development
    <details>
        <summary>Details</summary>
        When opening SOMA locally from the cloned repository, Protégé will follow the IRI redirects in the catalog file from the same folder. There, we redirected the imports from the online version to the local version. This allows offline editing, while not changing anything when opening the ontologies via the IRI elsewhere
    </details>
*   Feat: We now host and use our own, modified copy of `DUL`
    <details>
        <summary>Details</summary>
        <ul>
            <li>
                Reason: <code>DUL</code>is unreliable (e.g., down for a whole week and no one can open <code>SOMA</code>)
            </li>
            <li>
                As a side effect, we can make changes to <code>DUL</code>, if necessary (yes, we will be very careful)
                <ul>
                    <li>
                        Removed italian lables
                    </li>
                    <li>
                        Added missing annotations of `rdfs:isDefinedIn` (only for <code>DUL</code> concepts / roles)
                    </li>
                    <li>
                        Removed unnecessary annotations of author and date
                    </li>
                    <li>
                        Added missing english labels
                    </li>
                </ul>
        </ul>
    </details>
### 1.2.0
*   Base release for starting the changelog! Yay, now you get information why stuff might break!
    <details>
        <summary>Details</summary>
        The SOMA ontologies can now be accessed via a version IRI, e.g., <a href="INSERT VERSION IRI EXAMPLE HERE">INSERT_VERSION_IRI_EXAMPLE_HERE</a>. These are guaranteed to stay the way they are. The basic ontology IRI, e.g., <a href="http://www.ease-crc.org/ont/SOMA.owl">http://www.ease-crc.org/ont/SOMA.owl</a>, now refers to the newest version that is available (not neccessary associated to a stable release)
    </details>


## Further Information

SOMA-related papers, releases, metrics, and some tools can be accessed on the [SOMA Project Website](https://ease-crc.github.io/soma/)