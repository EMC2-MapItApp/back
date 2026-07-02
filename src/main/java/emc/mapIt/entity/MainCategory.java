package emc.mapIt.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "main_categories")
public class MainCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Column(nullable = false)
    private String icon;

    @NotBlank
    @Column(nullable = false)
    private String color;

    @OneToMany(
            mappedBy = "mainCategory",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<SubCategory> subCategories = new ArrayList<>();

}
